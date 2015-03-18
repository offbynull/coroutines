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
import com.offbynull.coroutines.instrumenter.asm.SimpleClassWriter;
import com.offbynull.coroutines.instrumenter.asm.VariableTable;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.addLabel;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.call;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.cloneInvokeNode;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.construct;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.ifIntegersEqual;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.jumpTo;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadIntConst;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadLocalVariableTable;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadVar;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadOperandStack;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.merge;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.pop;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.returnDummy;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.saveLocalVariableTable;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.saveVar;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.saveOperandStack;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.tableSwitch;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.throwException;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.findInvocationsOf;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.findInvocationsWithParameter;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.findMethodsWithParameter;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.searchForOpcodes;
import com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable;
import com.offbynull.coroutines.user.Continuation;
import static com.offbynull.coroutines.user.Continuation.MODE_NORMAL;
import static com.offbynull.coroutines.user.Continuation.MODE_SAVING;
import com.offbynull.coroutines.user.Continuation.MethodState;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SimpleVerifier;

public final class Instrumenter {

    private static final Type CONTINUATION_CLASS_TYPE = Type.getType(Continuation.class);
    private static final Type CONTINUATION_SUSPEND_METHOD_TYPE
            = Type.getType(MethodUtils.getAccessibleMethod(Continuation.class, "suspend"));
    private static final Method CONTINUATION_GETMODE_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "getMode");
    private static final Method CONTINUATION_SETMODE_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "setMode", Integer.TYPE);
    private static final Constructor<MethodState> METHODSTATE_INIT_METHOD
            = ConstructorUtils.getAccessibleConstructor(MethodState.class, Integer.TYPE, Object[].class, Object[].class);
    private static final Method CONTINUATION_ADDPENDING_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "addPending", MethodState.class);
    private static final Method CONTINUATION_REMOVELASTPENDING_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "removeLastPending");
    private static final Method CONTINUATION_REMOVEFIRSTSAVED_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "removeFirstSaved");
    private static final Method METHODSTATE_GETCONTINUATIONPOINT_METHOD
            = MethodUtils.getAccessibleMethod(MethodState.class, "getContinuationPoint");
    private static final Method METHODSTATE_GETLOCALTABLE_METHOD
            = MethodUtils.getAccessibleMethod(MethodState.class, "getLocalTable");
    private static final Method METHODSTATE_GETSTACK_METHOD
            = MethodUtils.getAccessibleMethod(MethodState.class, "getStack");

    private ClassInformationRepository classRepo;

    public Instrumenter(List<File> classpath) throws IOException {
        Validate.notNull(classpath);
        Validate.noNullElements(classpath);

        classRepo = ClassInformationRepository.create(classpath);
    }

    public byte[] instrument(byte[] input) {
        try {
            // Try catch finallies in older versions of Java use the JSR opcode which may cause us some grief, thankfully ASM has something
            // to automatically remove these JSR blocks. Remove them here before passing them off to the main instrumentation code.
            byte[] inputWithJsrBlocksRemoved = inlineJsrBlocks(input);
            
            return instrumentClass(inputWithJsrBlocksRemoved);
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe); // this should never happen
        }
    }

    private byte[] instrumentClass(byte[] input) throws IOException {
        Validate.notNull(input);
        Validate.isTrue(input.length > 0);

        ByteArrayInputStream bais = new ByteArrayInputStream(input);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Read class as tree model
        ClassReader cr = new ClassReader(bais);
        ClassNode classNode = new ClassNode();
        cr.accept(classNode, 0);

        // Don't do anything if interface
        if ((classNode.access & Opcodes.ACC_INTERFACE) == Opcodes.ACC_INTERFACE) {
            return input.clone();
        }

        // Find methods that need to be instrumented
        List<MethodNode> methodNodesToInstrument = findMethodsWithParameter(classNode.methods, CONTINUATION_CLASS_TYPE);

        for (MethodNode methodNode : methodNodesToInstrument) {
            // Check if method is constructor
            Validate.isTrue(!"<init>".equals(methodNode.name), "Instrumentation of constructors not allowed");

            // Check for JSR blocks -- Emitted for finally blocks in older versions of the JDK. Should never happen since we already inlined
            // these blocks before coming to this point. This is a sanity check.
            Validate.isTrue(searchForOpcodes(methodNode.instructions, Opcodes.JSR).isEmpty(),
                    "JSR instructions not allowed");
            
            // Check for synchronized code blocks. Synchronized methods and Java 5 locks are okay, synchronized code blocks aren't okay
            // because we can't reliably determine if we need to do a MONITOREXIT. It looks like Javaflow has some way of handling this.
            // More investigation is required.
            //
            // On further investigation, it seems that there's no reliable way to detect pairings of MONITORENTER and MONITOREXIT. This link
            // seems to explain it pretty well: http://mail.openjdk.java.net/pipermail/hotspot-runtime-dev/2008-April/000118.html. I've
            // confirmed by making a simple java class in JASMIN assembler that did 10 MONITORENTERs in a loop and returned. That code threw
            // IllegalMonitorStateException when it tried to return.
            Validate.isTrue(searchForOpcodes(methodNode.instructions, Opcodes.MONITORENTER, Opcodes.MONITOREXIT).isEmpty(),
                    "MONITORENTER/MONITOREXIT instructions are not allowed");

            // Get index of continuations object in parameter list
            // Get return type
            Type returnType = Type.getMethodType(methodNode.desc).getReturnType();

            // Analyze method
            Frame<BasicValue>[] frames = analyzeFrames(classNode.name, methodNode);

            // Find invocations of continuation points
            List<AbstractInsnNode> suspendInvocationInsnNodes
                    = findInvocationsOf(methodNode.instructions, CONTINUATION_SUSPEND_METHOD_TYPE);
            List<AbstractInsnNode> saveInvocationInsnNodes
                    = findInvocationsWithParameter(methodNode.instructions, CONTINUATION_CLASS_TYPE);

            // Check for invokedynamic instructions, which are currently only used by lambdas. See comments in validateNoInvokeDynamic to
            // see why we need to do this.
            validateNoInvokeDynamic(suspendInvocationInsnNodes);
            validateNoInvokeDynamic(saveInvocationInsnNodes);

            // Generate local variable indices
            VariableTable varTable = new VariableTable(classNode, methodNode);

            // Generate instructions for continuation points
            int nextId = 0;
            List<ContinuationPoint> continuationPoints = new LinkedList<>();

            for (AbstractInsnNode suspendInvocationInsnNode : suspendInvocationInsnNodes) {
                int insnIdx = methodNode.instructions.indexOf(suspendInvocationInsnNode);
                ContinuationPoint cp = new ContinuationPoint(true, nextId, suspendInvocationInsnNode, frames[insnIdx], returnType);
                continuationPoints.add(cp);
                nextId++;
            }

            for (AbstractInsnNode saveInvocationInsnNode : saveInvocationInsnNodes) {
                int insnIdx = methodNode.instructions.indexOf(saveInvocationInsnNode);
                ContinuationPoint cp = new ContinuationPoint(false, nextId, saveInvocationInsnNode, frames[insnIdx], returnType);
                continuationPoints.add(cp);
                nextId++;
            }

            // Manage new variables and arguments
            int continuationArgIdx = getLocalVariableIndexOfContinuationParameter(methodNode);
            Variable contArg = varTable.getArgument(continuationArgIdx);
            Variable methodStateVar = varTable.acquireExtra(Type.getType(MethodState.class));
            Variable savedLocalsVar = varTable.acquireExtra(Type.getType(Object[].class));
            Variable savedStackVar = varTable.acquireExtra(Type.getType(Object[].class));
            Variable tempObjVar = varTable.acquireExtra(Type.getType(Object.class));

            // Generate entrypoint instructions...
            //
            //    switch(continuation.getMode()) {
            //        case NORMAL: goto start
            //        case SAVING: throw exception
            //        case LOADING:
            //        {
            //            MethodState methodState = continuation.removeFirstSaved();
            //            Object[] stack = methodState.getStack();
            //            Object[] localVars = methodState.getLocalTable();
            //            switch(methodState.getContinuationPoint()) {
            //                case <number>:
            //                    restoreOperandStack(stack);
            //                    restoreLocalsStack(localVars);
            //                    goto restorePoint_<number>;
            //                ...
            //                ...
            //                ...
            //                default: throw exception
            //            }
            //            goto start;
            //        }
            //        default: throw exception
            //    }
            //
            //    start:
            //        ...
            //        ...
            //        ...
            LabelNode startOfMethodLabelNode = new LabelNode();
            InsnList entryPointInsnList
                    = merge(
                            tableSwitch(
                                    call(CONTINUATION_GETMODE_METHOD, loadVar(contArg)),
                                    throwException("Unrecognized state"),
                                    0,
                                    jumpTo(startOfMethodLabelNode),
                                    throwException("Unexpected state (saving not allowed at this point)"),
                                    merge(
                                            // debugPrint("calling remove first saved" + methodNode.name),
                                            call(CONTINUATION_REMOVEFIRSTSAVED_METHOD, loadVar(contArg)),
                                            saveVar(methodStateVar),
                                            call(METHODSTATE_GETLOCALTABLE_METHOD, loadVar(methodStateVar)),
                                            saveVar(savedLocalsVar),
                                            call(METHODSTATE_GETSTACK_METHOD, loadVar(methodStateVar)),
                                            saveVar(savedStackVar),
                                            tableSwitch(
                                                    call(METHODSTATE_GETCONTINUATIONPOINT_METHOD, loadVar(methodStateVar)),
                                                    throwException("Unrecognized restore id" + methodNode.name),
                                                    0,
                                                    continuationPoints.stream().map((cp) -> {
                                                        InsnList ret
                                                                = merge(
                                                                        loadOperandStack(savedStackVar, tempObjVar, cp.getFrame()),
                                                                        loadLocalVariableTable(savedLocalsVar, tempObjVar, cp.getFrame()),
                                                                        jumpTo(cp.getRestoreLabelNode())
                                                                );
                                                                return ret;
                                                    }).toArray((x) -> new InsnList[x])
                                            )
                                    // jump to not required here, switch above either throws exception or jumps to restore point
                                    )
                            ),
                            addLabel(startOfMethodLabelNode)
                    );
            methodNode.instructions.insert(entryPointInsnList);

            // Add store logic and restore addLabel for each continuation point
            //
            //      #IFDEF suspend
            //          Object[] stack = saveOperandStack();
            //          Object[] locals = saveLocalsStackHere();
            //          continuation.addPending(new MethodState(<number>, stack, locals);
            //          continuation.setMode(MODE_SAVING);
            //          return <dummy>;
            //          restorePoint_<number>:
            //          continuation.setMode(MODE_NORMAL);
            //      #ENDIF
            //
            //      #IFDEF !suspend
            //          restorePoint_<number>:
            //          Object[] stack = saveOperandStack();
            //          Object[] locals = saveLocalsStackHere();
            //          continuation.addPending(new MethodState(<number>, stack, locals);
            //          <method invocation>
            //          if (continuation.getMode() == MODE_SAVING) {
            //              return <dummy>;
            //          }
            //          continuation.removeLastPending();
            //      #ENDIF
            continuationPoints.forEach((cp) -> {
                InsnList saveBeforeInvokeInsnList
                        = merge(
                                // debugPrint("saving operand stack" + methodNode.name),
                                saveOperandStack(savedStackVar, tempObjVar, cp.getFrame()),
                                // debugPrint("saving locals" + methodNode.name),
                                saveLocalVariableTable(savedLocalsVar, tempObjVar, cp.getFrame()),
                                // debugPrint("calling addIndividual pending" + methodNode.name),
                                call(CONTINUATION_ADDPENDING_METHOD, loadVar(contArg),
                                        construct(METHODSTATE_INIT_METHOD,
                                                loadIntConst(cp.getId()),
                                                loadVar(savedStackVar),
                                                loadVar(savedLocalsVar)))
                        );

                InsnList insnList;
                if (cp.isSuspend()) {
                    // When Continuation.suspend() is called, it's a termination point. We want to ...
                    //
                    //    1. Save our stack, locals, and restore point and push them on to the Continuation object.
                    //    2. Put the continuation in to SAVING mode.
                    //    3. Remove the call to suspend() and return a dummy value in its place.
                    //    4. Add a label just after the return we added (used by loading code when execution is continued).
                    //
                    // By going in to SAVING mode and returning, callers up the chain will know to stop their flow of execution and return
                    // immediately (see else block below)
                    insnList
                            = merge(
                                    saveBeforeInvokeInsnList, // save
                                    // set saving mode
                                    // debugPrint("setting mode to saving" + methodNode.name),
                                    call(CONTINUATION_SETMODE_METHOD, loadVar(contArg), loadIntConst(MODE_SAVING)),
                                    // debugPrint("returning dummy value" + methodNode.name),
                                    returnDummy(returnType), // return dummy value
                                    addLabel(cp.getRestoreLabelNode()), // addIndividual restore point for when in loading mode
                                    // debugPrint("entering restore point" + methodNode.name),
                                    pop(), // frame at the time of invocation to Continuation.suspend() has Continuation reference on the
                                    // stack that would have been consumed by that invocation... since we're removing that call, we
                                    // also need to pop the Continuation reference from the stack... it's important that we
                                    // explicitly do it at this point becuase during loading the stack will be restored with top
                                    // of stack pointing to that continuation object
                                    // debugPrint("going back in to normal mode" + methodNode.name),
                                    // we're back in to a loading state now
                                    call(CONTINUATION_SETMODE_METHOD, loadVar(contArg), loadIntConst(MODE_NORMAL))
                            );
                } else {
                    // When a method that takes in a Continuation object as a parameter is called, We want to ...
                    //
                    //    1. Add a label (used by the loading code when execution is continued).
                    //    2. Save our stack, locals, and restore point and push them on to the Continuation object.
                    //    3. Call the method as it normally would be.
                    //    4. Once the method returns, we check to see if the method is in SAVING mode and return immediately if it is. If it
                    //       isn't, then this is the normal flow of execution and the save we did in step 2 isn't nessecary so remove it.
                    //
                    // If in SAVING mode, it means that suspend() was invoked somewhere down the chain. Callers above it must stop their
                    // normal flow of execution and return immediately.
                    insnList
                            = merge(
                                    addLabel(cp.getRestoreLabelNode()), // addIndividual restore point for when in loading mode
                                    saveBeforeInvokeInsnList, // save
                                    // debugPrint("invoking" + methodNode.name),
                                    cloneInvokeNode(cp.getInvokeInsnNode()), // invoke method
                                    // debugPrint("testing if in saving mode" + methodNode.name),
                                    ifIntegersEqual(// if we're saving after invoke, return dummy value
                                            call(CONTINUATION_GETMODE_METHOD, loadVar(contArg)),
                                            loadIntConst(MODE_SAVING),
                                            returnDummy(returnType)
                                    ),
                                    // debugPrint("calling remove last pending" + methodNode.name),
                                    call(CONTINUATION_REMOVELASTPENDING_METHOD, loadVar(contArg)) // otherwise assume we're normal, and
                            // remove the state we added on to
                            // pending
                            );
                }

                methodNode.instructions.insertBefore(cp.getInvokeInsnNode(), insnList);
                methodNode.instructions.remove(cp.getInvokeInsnNode());
            });
        }

        // Write tree model back out as class
        ClassWriter cw = new SimpleClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES, classRepo);
        classNode.accept(cw);

        baos.write(cw.toByteArray());

        return baos.toByteArray();
    }

    private byte[] inlineJsrBlocks(byte[] input) {
        ClassReader reader = new ClassReader(input);
        ClassWriter writer = new SimpleClassWriter(0, classRepo);
        
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM5, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor origVisitor = super.visitMethod(access, name, desc, signature, exceptions);
                return new JSRInlinerAdapter(origVisitor, access, name, desc, signature, exceptions);
            }
        };
        reader.accept(visitor, 0);
        
        return writer.toByteArray();
    }

    private Frame[] analyzeFrames(String className, MethodNode methodNode) {
        try {
            return new Analyzer<>(new SimpleVerifier()).analyze(className, methodNode);
        } catch (AnalyzerException ae) {
            throw new IllegalArgumentException("Analyzer failed to analyze method", ae);
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
