/*
 * Copyright (c) 2015, Kasra Faghihi, All rights reserved.
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
import com.offbynull.coroutines.instrumenter.asm.FileSystemClassInformationRepository;
import com.offbynull.coroutines.instrumenter.asm.SimpleClassWriter;
import com.offbynull.coroutines.instrumenter.asm.VariableTable;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.findInvocationsOf;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.findInvocationsWithParameter;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.findMethodsWithParameter;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.searchForOpcodes;
import com.offbynull.coroutines.instrumenter.asm.SimpleClassNode;
import com.offbynull.coroutines.instrumenter.asm.SimpleVerifier;
import com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable;
import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.MethodState;
import com.offbynull.coroutines.user.Instrumented;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

/**
 * Instruments methods in Java classes that are intended to be run as coroutines. Tested with Java 1.2 and Java 8, so hopefully thing should
 * work with all versions of Java inbetween.
 * @author Kasra Faghihi
 */
public final class Instrumenter {

    private static final Type INSTRUMENTED_CLASS_TYPE = Type.getType(Instrumented.class);
    private static final Type CONTINUATION_CLASS_TYPE = Type.getType(Continuation.class);
    private static final Method CONTINUATION_SUSPEND_METHOD = MethodUtils.getAccessibleMethod(Continuation.class, "suspend");

    private ClassInformationRepository classRepo;

    /**
     * Constructs a {@link Instrumenter} object from a filesystem classpath (folders and JARs).
     * @param classpath classpath JARs and folders to use for instrumentation (this is needed by ASM to generate stack map frames).
     * @throws IOException if classes in the classpath could not be loaded up
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     */
    public Instrumenter(List<File> classpath) throws IOException {
        Validate.notNull(classpath);
        Validate.noNullElements(classpath);

        classRepo = FileSystemClassInformationRepository.create(classpath);
    }

    /**
     * Constructs a {@link Instrumenter} object.
     * @param repo class information repository (this is needed by ASM to generate stack map frames).
     * @throws NullPointerException if any argument is {@code null}
     */
    public Instrumenter(ClassInformationRepository repo) {
        Validate.notNull(repo);

        classRepo = repo;
    }

    /**
     * Instruments a class.
     * @param input class file contents
     * @return instrumented class
     * @throws IllegalArgumentException if the class could not be instrumented for some reason
     * @throws NullPointerException if any argument is {@code null}
     */
    public byte[] instrument(byte[] input) {
        Validate.notNull(input);
        Validate.isTrue(input.length > 0);
        
        // Read class as tree model -- because we're using SimpleClassNode, JSR blocks get inlined
        ClassReader cr = new ClassReader(input);
        ClassNode classNode = new SimpleClassNode();
        cr.accept(classNode, 0);

        // Is this class an interface? if so, skip it
        if ((classNode.access & Opcodes.ACC_INTERFACE) == Opcodes.ACC_INTERFACE) {
            return input.clone();
        }

        // Has this class already been instrumented? if so, skip it
        if (classNode.interfaces.contains(INSTRUMENTED_CLASS_TYPE.getInternalName())) {
            return input.clone();
        }

        // Find methods that need to be instrumented. If none are found, skip
        List<MethodNode> methodNodesToInstrument = findMethodsWithParameter(classNode.methods, CONTINUATION_CLASS_TYPE);
        if (methodNodesToInstrument.isEmpty()) {
            return input.clone();
        }
        
        // Add the "Instrumented" interface to this class so if we ever come back to it, we can skip it
        classNode.interfaces.add(INSTRUMENTED_CLASS_TYPE.getInternalName());

        // Instrument each method that was returned
        for (MethodNode methodNode : methodNodesToInstrument) {
            // Check if method is constructor -- we cannot instrument constructor
            Validate.isTrue(!"<init>".equals(methodNode.name), "Instrumentation of constructors not allowed");

            // Check for JSR blocks -- Emitted for finally blocks in older versions of the JDK. Should never happen since we already inlined
            // these blocks before coming to this point. This is a sanity check.
            Validate.isTrue(searchForOpcodes(methodNode.instructions, Opcodes.JSR).isEmpty(),
                    "JSR instructions not allowed");
            
            // Find invocations of continuation points
            List<AbstractInsnNode> suspendInvocationInsnNodes
                    = findInvocationsOf(methodNode.instructions, CONTINUATION_SUSPEND_METHOD);
            List<AbstractInsnNode> invokeInvocationInsnNodes
                    = findInvocationsWithParameter(methodNode.instructions, CONTINUATION_CLASS_TYPE);
            
            // If there are no continuation points, we don't need to instrument this method. It'll be like any other normal method
            // invocation because it won't have the potential to pause or call in to another method that may potentially pause.
            if (suspendInvocationInsnNodes.isEmpty() && invokeInvocationInsnNodes.isEmpty()) {
                continue;
            }
            
            // Check for continuation points that use invokedynamic instruction, which are currently only used by lambdas. See comments in
            // validateNoInvokeDynamic to see why we need to do this.
            validateNoInvokeDynamic(suspendInvocationInsnNodes);
            validateNoInvokeDynamic(invokeInvocationInsnNodes);
            
            // Analyze method
            Frame<BasicValue>[] frames;
            try {
                frames = new Analyzer<>(new SimpleVerifier(classRepo)).analyze(classNode.name, methodNode);
            } catch (AnalyzerException ae) {
                throw new IllegalArgumentException("Analyzer failed to analyze method", ae);
            }
            
            // Manage arguments and additional local variables that we need for instrumentation
            int contArgIdx = getLocalVariableIndexOfContinuationParameter(methodNode);
            
            VariableTable varTable = new VariableTable(classNode, methodNode);
            Variable contArg = varTable.getArgument(contArgIdx); // Continuation argument
            Variable methodStateVar = varTable.acquireExtra(MethodState.class); // var shared between monitor and flow instrumentation
            Variable tempObjVar = varTable.acquireExtra(Object.class); // var shared between monitor and flow instrumentation
                   
            // Generate code to deal with suspending around synchronized blocks
            MonitorInstrumentationVariables monitorInstrumentationVariables = new MonitorInstrumentationVariables(
                    varTable,
                    methodStateVar,
                    tempObjVar);
            MonitorInstrumentationInstructions monitorInstrumentationLogic = new MonitorInstrumentationGenerator(
                    methodNode,
                    monitorInstrumentationVariables)
                    .generate();
            
            // Generate code to deal with flow control (makes use of some of the code generated in monitorInstrumentationLogic)
            FlowInstrumentationVariables flowInstrumentationVariables = new FlowInstrumentationVariables(
                    varTable,
                    contArg,
                    methodStateVar,
                    tempObjVar);
            FlowInstrumentationInstructions flowInstrumentationInstructions = new FlowInstrumentationGenerator(
                    methodNode,
                    suspendInvocationInsnNodes,
                    invokeInvocationInsnNodes,
                    frames,
                    monitorInstrumentationLogic,
                    flowInstrumentationVariables)
                    .generate();
            
            // Apply generated code
            applyInstrumentationLogic(methodNode, flowInstrumentationInstructions, monitorInstrumentationLogic);
        }

        // Write tree model back out as class
        ClassWriter cw = new SimpleClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES, classRepo);
        classNode.accept(cw);
        return cw.toByteArray();
    }
    
    private void applyInstrumentationLogic(MethodNode methodNode,
            FlowInstrumentationInstructions flowInstrumentationLogic,
            MonitorInstrumentationInstructions monitorInstrumentationLogic) {
        
        // Add trycatch nodes
        for (TryCatchBlockNode tryCatchBlockNode : flowInstrumentationLogic.getInvokeTryCatchBlockNodes()) {
            methodNode.tryCatchBlocks.add(0, tryCatchBlockNode);
        }
        
        // Add loading code
        InsnList entryPointInsnList = flowInstrumentationLogic.getEntryPointInsnList();
        methodNode.instructions.insert(entryPointInsnList);
        
        // Add instrumented method invocations
        Map<AbstractInsnNode, InsnList> invokeReplacements = flowInstrumentationLogic.getInvokeInsnNodeReplacements();
        for (Entry<AbstractInsnNode, InsnList> replaceEntry : invokeReplacements.entrySet()) {
            AbstractInsnNode nodeToReplace = replaceEntry.getKey();
            InsnList insnsToReplaceWith = replaceEntry.getValue();
            
            methodNode.instructions.insertBefore(nodeToReplace, insnsToReplaceWith);
            methodNode.instructions.remove(nodeToReplace);
        }
        
        // Add instrumented monitorenter/monitorexits instructions
        Map<AbstractInsnNode, InsnList> monitorReplacements = monitorInstrumentationLogic.getMonitorInsnNodeReplacements();
        for (Entry<AbstractInsnNode, InsnList> replaceEntry : monitorReplacements.entrySet()) {
            AbstractInsnNode nodeToReplace = replaceEntry.getKey();
            InsnList insnsToReplaceWith = replaceEntry.getValue();
            
            methodNode.instructions.insertBefore(nodeToReplace, insnsToReplaceWith);
            methodNode.instructions.remove(nodeToReplace);
        }
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
}
