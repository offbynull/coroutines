/*
 * Copyright (c) 2016, Kasra Faghihi, All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.offbynull.coroutines.instrumenter;

import com.offbynull.coroutines.instrumenter.asm.ClassInformationRepository;
import static com.offbynull.coroutines.instrumenter.asm.MethodInvokeUtils.getReturnTypeOfInvocation;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.findInvocationsOf;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.findInvocationsWithParameter;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.findLineNumberForInstruction;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.findTryCatchBlockNodesEncompassingInstruction;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.searchForOpcodes;
import com.offbynull.coroutines.instrumenter.asm.SimpleVerifier;
import com.offbynull.coroutines.instrumenter.asm.VariableTable;
import com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable;
import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.LockState;
import com.offbynull.coroutines.user.MethodState;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import static org.apache.commons.collections4.CollectionUtils.union;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

final class MethodAnalyzer {
    
    private static final Type CONTINUATION_CLASS_TYPE = Type.getType(Continuation.class);
    private static final Method CONTINUATION_SUSPEND_METHOD = MethodUtils.getAccessibleMethod(Continuation.class, "suspend");
    
    private final ClassInformationRepository classInfoRepo;
    
    MethodAnalyzer(ClassInformationRepository classInfoRepo) {
        Validate.notNull(classInfoRepo);

        this.classInfoRepo = classInfoRepo;
    }

    public MethodAttributes analyze(ClassNode classNode, MethodNode methodNode, InstrumentationSettings settings) {
        ///////////////////////////////////////////////////////////////////////////////////////////
        // VALIDATE INPUTS
        ///////////////////////////////////////////////////////////////////////////////////////////
        
        Validate.notNull(classNode);
        Validate.notNull(methodNode);
        Validate.notNull(settings);
        
        // Sanity check to make sure class
        Validate.isTrue(classNode.methods.contains(methodNode), "Method does not belong to class");

        // Check if method is constructor -- we cannot instrument constructor
        Validate.isTrue(!"<init>".equals(methodNode.name), "Instrumentation of constructors not allowed");

        // Check for JSR blocks -- Emitted for finally blocks in older versions of the JDK. Should never happen since we already inlined
        // these blocks before coming to this point. This is a sanity check.
        Validate.isTrue(searchForOpcodes(methodNode.instructions, Opcodes.JSR).isEmpty(), "JSR instructions not allowed");




        ///////////////////////////////////////////////////////////////////////////////////////////
        // FIND SUSPEND / CONTINUATION INVOCATIONS + ALSO FIND SYNCHRONIZATION INSTRUCTIONS
        ///////////////////////////////////////////////////////////////////////////////////////////
        
        // Find invocations of continuation invocation/suspend
        List<AbstractInsnNode> suspendInvocationInsnNodes
                = findInvocationsOf(methodNode.instructions, CONTINUATION_SUSPEND_METHOD);
        List<AbstractInsnNode> contInvocationInsnNodes
                = findInvocationsWithParameter(methodNode.instructions, CONTINUATION_CLASS_TYPE);

        // If there are no continuation points, we don't need to instrument this method. It'll be like any other normal method
        // invocation because it won't have the potential to pause or call in to another method that may potentially pause.
        if (suspendInvocationInsnNodes.isEmpty() && contInvocationInsnNodes.isEmpty()) {
            return null;
        }

        // Check for continuation points that use invokedynamic instruction, which are currently only used by lambdas. See comments in
        // validateNoInvokeDynamic to see why we need to do this.
        validateNoInvokeDynamic(suspendInvocationInsnNodes);
        validateNoInvokeDynamic(contInvocationInsnNodes);

        // Find MONITORENTER/MONITOREXIT instructions
        List<AbstractInsnNode> monitorInsnNodes = searchForOpcodes(methodNode.instructions, Opcodes.MONITORENTER, Opcodes.MONITOREXIT);




        ///////////////////////////////////////////////////////////////////////////////////////////
        // COMPUTE FRAMES FOR INSTRUCTIONS
        ///////////////////////////////////////////////////////////////////////////////////////////

        // Compute frames for each instruction in the method
        Frame<BasicValue>[] frames;
        try {
            frames = new Analyzer<>(new SimpleVerifier(classInfoRepo)).analyze(classNode.name, methodNode);
        } catch (AnalyzerException ae) {
            throw new IllegalArgumentException("Analyzer failed to analyze method", ae);
        }




        ///////////////////////////////////////////////////////////////////////////////////////////
        // CREATE SUSPEND/CONTINUATION/SYNCHRONIZATION OBJECTS
        ///////////////////////////////////////////////////////////////////////////////////////////

        List<ContinuationPoint> continuationPoints = new LinkedList<>();
        // Create SuspendContinuationPoint objects for suspend points
        
        for (AbstractInsnNode suspendInvocationInsnNode : suspendInvocationInsnNodes) {
            int instructionIndex = methodNode.instructions.indexOf(suspendInvocationInsnNode);
            Frame<BasicValue> frame = frames[instructionIndex];
            
            LineNumberNode lineNumberNode = findLineNumberForInstruction(methodNode.instructions, suspendInvocationInsnNode);
            Integer lineNumber = lineNumberNode != null ? lineNumberNode.line : null;
            
            SuspendContinuationPoint suspendPoint = new SuspendContinuationPoint(
                    lineNumber, (MethodInsnNode) suspendInvocationInsnNode, frame);
            continuationPoints.add(suspendPoint);
        }

        // Create NormalInvokeContinuationPoint / TryCatchInvokeContinuationPoint objects for suspend points
        for (AbstractInsnNode contInvocationInsnNode : contInvocationInsnNodes) {
            int instructionIndex = methodNode.instructions.indexOf(contInvocationInsnNode);
            boolean withinTryCatch = findTryCatchBlockNodesEncompassingInstruction(
                    methodNode.instructions,
                    methodNode.tryCatchBlocks,
                    contInvocationInsnNode).size() > 0;
            Frame<BasicValue> frame = frames[instructionIndex];
            
            LineNumberNode lineNumberNode = findLineNumberForInstruction(methodNode.instructions, contInvocationInsnNode);
            Integer lineNumber = lineNumberNode != null ? lineNumberNode.line : null;
            
            ContinuationPoint continuationPoint;
            if (withinTryCatch) {
                continuationPoint = new TryCatchInvokeContinuationPoint(
                        lineNumber, (MethodInsnNode) contInvocationInsnNode, frame);
            } else {
                continuationPoint = new NormalInvokeContinuationPoint(
                        lineNumber, (MethodInsnNode) contInvocationInsnNode, frame);
            }
            continuationPoints.add(continuationPoint);
        }

        // Create SynchronizationPoint objects for MONITORENTER/MONITOREXIT opcodes
        List<SynchronizationPoint> synchPoints = new LinkedList<>();
        for (AbstractInsnNode monitorInsnNode : monitorInsnNodes) {
            int instructionIndex = methodNode.instructions.indexOf(monitorInsnNode);
            Frame<BasicValue> frame = frames[instructionIndex];
            
            SynchronizationPoint synchPoint = new SynchronizationPoint((InsnNode) monitorInsnNode, frame);
            synchPoints.add(synchPoint);
        }




        ///////////////////////////////////////////////////////////////////////////////////////////
        // DETERMINE TYPES RETURNED FROM SUSPEND / CONTINUATION POINTS
        ///////////////////////////////////////////////////////////////////////////////////////////

        // For each non-suspend invocation node found, see what the return type is and figure out if it's in a try/catch block.
        //
        // The return type scanning is needed because the instrumenter needs to temporarily cache the results of the invocation. The
        // variable slots for these caches are assigned lower on in the code.
        //
        // The try/catch information is needed because the instrumenter needs to temporarily cache a throwable if the invocation throws one.
        // The variable slot for the throwable is assigned lower on in the code.
        TypeTracker invocationReturnTypes = new TypeTracker();
        boolean invocationFoundWrappedInTryCatch = false;
        for (AbstractInsnNode invokeInsnNode : contInvocationInsnNodes) {
            if (findTryCatchBlockNodesEncompassingInstruction(
                    methodNode.instructions,
                    methodNode.tryCatchBlocks,
                    invokeInsnNode).size() > 0) {
                invocationFoundWrappedInTryCatch = true;
            }
            
            Type returnType = getReturnTypeOfInvocation(invokeInsnNode);
            if (returnType.getSort() != Type.VOID) {
                invocationReturnTypes.trackType(returnType);
            }
        }




        ///////////////////////////////////////////////////////////////////////////////////////////
        // DETERMINE TYPES ON THE LOCAL VARIABLES TABE AT SUSPEND / CONTINUATION POINTS
        ///////////////////////////////////////////////////////////////////////////////////////////

        // For each invocation node found, see whats on the locals.
        //
        // We need on scan the types on the locals because the instrumenter needs to know which extra variable slots to allot to the
        // storage containers for those types. The variable slots for these storage containers are assigned lower on in the code.
        TypeTracker localsTypes = new TypeTracker();
        for (AbstractInsnNode invokeInsnNode : union(contInvocationInsnNodes, suspendInvocationInsnNodes)) {
            int instructionIndex = methodNode.instructions.indexOf(invokeInsnNode);
            Frame<BasicValue> frame = frames[instructionIndex];

            for (int i = 0; i < frame.getLocals(); i++) {
                BasicValue basicValue = frame.getLocal(i);
                Type type = basicValue.getType();
                
                // If type == null, basicValue is pointing to uninitialized var -- basicValue.toString() will return '.'. This means that
                // this slot contains nothing to save. We don't store anything in this case so skip this slot if we encounter it.
                //
                // If type is 'Lnull;', this means that the slot has been assigned null and that "there has been no merge yet that would
                // 'raise' the type toward some class or interface type" (from ASM mailing list). We know this slot will always contain null
                // at this point in the code so we can avoid saving it. When we load it back up, we can simply push a null in to that slot,
                // thereby keeping the same 'Lnull;' type.
                if (type == null || "Lnull;".equals(type.getDescriptor())) {
                    continue;
                }
                
                localsTypes.trackType(type);
            }
        }



        ///////////////////////////////////////////////////////////////////////////////////////////
        // DETERMINE TYPES ON THE OPERAND STACK AT SUSPEND / CONTINUATION POINTS
        ///////////////////////////////////////////////////////////////////////////////////////////

        // For each invocation node found, see whats on the operand stack.
        //
        // We need on scan the types on the operand stack because the instrumenter needs to know which extra variable slots to allot to the
        // storage containers for those types. The variable slots for these storage containers are assigned lower on in the code.
        TypeTracker operandStackTypes = new TypeTracker();
        for (AbstractInsnNode invokeInsnNode : union(contInvocationInsnNodes, suspendInvocationInsnNodes)) {
            int instructionIndex = methodNode.instructions.indexOf(invokeInsnNode);
            Frame<BasicValue> frame = frames[instructionIndex];

            for (int i = 0; i < frame.getStackSize(); i++) {
                BasicValue basicValue = frame.getStack(i);
                Type type = basicValue.getType();
                
                // If type is 'Lnull;', this means that the slot has been assigned null and that "there has been no merge yet that would
                // 'raise' the type toward some class or interface type" (from ASM mailing list). We know this slot will always contain null
                // at this point in the code so we can avoid saving it. When we load it back up, we can simply push a null in to that slot,
                // thereby keeping the same 'Lnull;' type.
                if ("Lnull;".equals(type.getDescriptor())) {
                    continue;
                }
                
                operandStackTypes.trackType(type);
            }
        }




        ///////////////////////////////////////////////////////////////////////////////////////////
        // DETERMINE WHICH INDEX IN LOCAL VARIABLE TABLE CONTAINS CONTINUATION OBJECT
        ///////////////////////////////////////////////////////////////////////////////////////////
        
        // Find index of continuation object
        int contArgIdx = getLocalVariableIndexOfContinuationParameter(methodNode);




        ///////////////////////////////////////////////////////////////////////////////////////////
        // CALCULATE EXTRA VARIABLES REQUIRED BY INSTRUMENTATION
        ///////////////////////////////////////////////////////////////////////////////////////////

        VariableTable varTable = new VariableTable(classNode, methodNode);
        
        // Create variable for the continuation object passed in as arg + variable for storing/loading method state
        Variable continuationArgVar = varTable.getArgument(contArgIdx);
        Variable methodStateVar = varTable.acquireExtra(MethodState.class);
        CoreVariables coreVars = new CoreVariables(
                continuationArgVar,
                methodStateVar);
        
        // Create variables for storing/loading locals -- only create ones we need
        StorageVariables localsStorageVars = allocateStorageVariableSlots(varTable, localsTypes);

        // Create variables for storing/loading operand stack -- only create ones we need
        StorageVariables stackStorageVars = allocateStorageVariableSlots(varTable, operandStackTypes);
        
        // Create variables to locals and operand stack storage containers -- these must exist
        StorageContainerVariables storageContainerVars = allocateStorageContainerVariableSlots(varTable);

        // Create variables to cache return values and thrown exceptions of invocations -- only create ones we need
        CacheVariables cacheVars = allocateCacheVariableSlots(varTable, invocationReturnTypes, invocationFoundWrappedInTryCatch);
        
        // Create variables to for holding on to monitors -- only create if we need them
        LockVariables lockVars = allocateLockVariableSlots(varTable, !synchPoints.isEmpty());
        




        ///////////////////////////////////////////////////////////////////////////////////////////
        // RETURN RESULTS OF ANALYSIS
        ///////////////////////////////////////////////////////////////////////////////////////////
        
        MethodSignature signature = new MethodSignature(classNode.name, methodNode.name, Type.getMethodType(methodNode.desc));

        return new MethodAttributes(
                signature,
                settings,
                continuationPoints,
                synchPoints,
                coreVars,
                cacheVars,
                storageContainerVars,
                localsStorageVars,
                stackStorageVars,
                lockVars);
    }
    
    private int getLocalVariableIndexOfContinuationParameter(MethodNode methodNode) {
        // If it is NOT static, the first index in the local variables table is always the "this" pointer, followed by the arguments passed
        // in to the method.
        // If it is static, the local variables table doesn't contain the "this" pointer, just the arguments passed in to the method.
        boolean isStatic = (methodNode.access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC;
        Type[] argumentTypes = Type.getMethodType(methodNode.desc).getArgumentTypes();

        int idx = -1;
        for (int i = 0; i < argumentTypes.length; i++) {
            Type type = argumentTypes[i];
            if (type.equals(CONTINUATION_CLASS_TYPE)) {
                if (idx == -1) {
                    idx = i;
                } else {
                    // should never really happen because we should be checking before calling this method
                    throw new IllegalArgumentException("Multiple Continuation arguments found in method " + methodNode.name);
                }
            }
        }

        return isStatic ? idx : idx + 1;
    }
    
    private CacheVariables allocateCacheVariableSlots(
            VariableTable varTable,
            TypeTracker invocationReturnTypes,
            boolean invocationFoundWrappedInTryCatch) {
        Variable intReturnCacheVar = null;
        Variable longReturnCacheVar = null;
        Variable floatReturnCacheVar = null;
        Variable doubleReturnCacheVar = null;
        Variable objectReturnCacheVar = null;
        Variable throwableCacheVar = null;
        if (invocationReturnTypes.intFound) {
            intReturnCacheVar = varTable.acquireExtra(Integer.TYPE);
        }
        if (invocationReturnTypes.longFound) {
            longReturnCacheVar = varTable.acquireExtra(Long.TYPE);
        }
        if (invocationReturnTypes.floatFound) {
            floatReturnCacheVar = varTable.acquireExtra(Float.TYPE);
        }
        if (invocationReturnTypes.doubleFound) {
            doubleReturnCacheVar = varTable.acquireExtra(Double.TYPE);
        }
        if (invocationReturnTypes.objectFound) {
            objectReturnCacheVar = varTable.acquireExtra(Object.class);
        }
        if (invocationFoundWrappedInTryCatch) {
            throwableCacheVar = varTable.acquireExtra(Throwable.class);
        }

        return new CacheVariables(
                intReturnCacheVar,
                longReturnCacheVar,
                floatReturnCacheVar,
                doubleReturnCacheVar,
                objectReturnCacheVar,
                throwableCacheVar);
    }
    
    private StorageVariables allocateStorageVariableSlots(
            VariableTable varTable,
            TypeTracker storageTypes) {
        Variable intStorageVar = null;
        Variable longStorageVar = null;
        Variable floatStorageVar = null;
        Variable doubleStorageVar = null;
        Variable objectStorageVar = null;
        if (storageTypes.intFound) {
            intStorageVar = varTable.acquireExtra(int[].class);
        }
        if (storageTypes.longFound) {
            longStorageVar = varTable.acquireExtra(long[].class);
        }
        if (storageTypes.floatFound) {
            floatStorageVar = varTable.acquireExtra(float[].class);
        }
        if (storageTypes.doubleFound) {
            doubleStorageVar = varTable.acquireExtra(double[].class);
        }
        if (storageTypes.objectFound) {
            objectStorageVar = varTable.acquireExtra(Object[].class);
        }

        return new StorageVariables(
                intStorageVar,
                longStorageVar,
                floatStorageVar,
                doubleStorageVar,
                objectStorageVar);
    }

    private StorageContainerVariables allocateStorageContainerVariableSlots(
            VariableTable varTable) {
        Variable containerVar = varTable.acquireExtra(Object[].class);

        return new StorageContainerVariables(containerVar);
    }

    private LockVariables allocateLockVariableSlots(
            VariableTable varTable,
            boolean containsSyncPoints) {
        Variable lockStateVar = null;
        Variable lockCounterVar = null;
        Variable lockArrayLenVar = null;

        if (containsSyncPoints) {
            lockStateVar = varTable.acquireExtra(LockState.class);
            lockCounterVar = varTable.acquireExtra(Type.INT_TYPE);
            lockArrayLenVar = varTable.acquireExtra(Type.INT_TYPE);
        }

        return new LockVariables(
                lockStateVar,
                lockCounterVar,
                lockArrayLenVar);
    }
    
    private void validateNoInvokeDynamic(List<AbstractInsnNode> insnNodes) {
        // Why is invokedynamic not allowed? because apparently invokedynamic can map to anything... which means that we can't reliably
        // determine if what is being called by invokedynamic is going to be a method we expect to be instrumented to handle Continuations.
        //
        // In Java8, this is the case for lambdas. Lambdas get translated to invokedynamic calls when they're created. Take the following
        // Java code as an example...
        //
        // public void run(Continuation c) {
        //     String temp = "hi";
        //     builder.append("started\n");
        //     for (int i = 0; i < 10; i++) {
        //         Consumer<Integer> consumer = (x) -> {
        //             temp.length(); // pulls in temp as an arg, which causes c (the Continuation object) to go in as a the second argument
        //             builder.append(x).append('\n');
        //             System.out.println("XXXXXXX");
        //             c.suspend();
        //         }
        //         consumer.accept(i);
        //     }
        // }
        //
        // This for loop in the above code maps out to...
        //    L5
        //     LINENUMBER 18 L5
        //     ALOAD 0: this
        //     ALOAD 2: temp
        //     ALOAD 1: c
        //     INVOKEDYNAMIC accept(LambdaInvokeTest, String, Continuation) : Consumer [
        //       // handle kind 0x6 : INVOKESTATIC
        //       LambdaMetafactory.metafactory(MethodHandles$Lookup, String, MethodType, MethodType, MethodHandle, MethodType) : CallSite
        //       // arguments:
        //       (Object) : void, 
        //       // handle kind 0x7 : INVOKESPECIAL
        //       LambdaInvokeTest.lambda$0(String, Continuation, Integer) : void, 
        //       (Integer) : void
        //     ]
        //     ASTORE 4
        //    L6
        //     LINENUMBER 24 L6
        //     ALOAD 4: consumer
        //     ILOAD 3: i
        //     INVOKESTATIC Integer.valueOf (int) : Integer
        //     INVOKEINTERFACE Consumer.accept (Object) : void
        //    L7
        //     LINENUMBER 17 L7
        //     IINC 3: i 1
        //    L4
        //     ILOAD 3: i
        //     BIPUSH 10
        //     IF_ICMPLT L5
        //
        // Even though the invokedynamic instruction is calling a method called "accept", it doesn't actually call Consumer.accept().
        // Instead it just creates the Consumer object that accept() is eventually called on. This means that it makes no sense to add
        // instrumentation around invokedynamic because it isn't calling what we expected it to call. When accept() does eventually get
        // called, it doesn't take in a Continuation object as a parameter so instrumentation won't be added in around it.
        //
        // There's no way to reliably instrument around the accept() method because we don't know if an accept() invocation will be to a
        // Consumer that we've instrumented.
        //
        // The instrumenter identifies which methods to instrument and which method invocations to instrument by checking to see if they
        // explicitly take in a Continuation as a parameter. Using lambdas like this is essentially like creating an implementation of
        // Consumer as a class and setting the Continuation object as a field in that class. Cases like that cannot be reliably
        // identified for instrumentation.

        for (AbstractInsnNode insnNode : insnNodes) {
            if (insnNode instanceof InvokeDynamicInsnNode) {
                throw new IllegalArgumentException("INVOKEDYNAMIC instructions are not allowed");
            }
        }
    }

    private static final class TypeTracker {
        private boolean intFound = false;
        private boolean longFound = false;
        private boolean floatFound = false;
        private boolean doubleFound = false;
        private boolean objectFound = false;
        
        public void trackType(Type type) {
            switch (type.getSort()) {
                case Type.BOOLEAN:
                case Type.BYTE:
                case Type.CHAR:
                case Type.SHORT:
                case Type.INT:
                    intFound = true;
                    break;
                case Type.LONG:
                    longFound = true;
                    break;
                case Type.FLOAT:
                    floatFound = true;
                    break;
                case Type.DOUBLE:
                    doubleFound = true;
                    break;
                case Type.OBJECT:
                case Type.ARRAY:
                    objectFound = true;
                    break;
                case Type.VOID:
                case Type.METHOD:
                default:
                    throw new IllegalArgumentException(); // this should never happen
                }
        }
    }
}
