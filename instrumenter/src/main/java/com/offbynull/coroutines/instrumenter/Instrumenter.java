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
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.cloneInsnList;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.cloneInvokeNode;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.cloneMonitorNode;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.construct;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.empty;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.forEach;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.ifIntegersEqual;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.jumpTo;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadIntConst;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadLocalVariableTable;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadNull;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadVar;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadOperandStack;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.merge;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.monitorEnter;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.monitorExit;
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
import com.offbynull.coroutines.instrumenter.asm.SimpleClassNode;
import com.offbynull.coroutines.instrumenter.asm.SimpleVerifier;
import com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable;
import com.offbynull.coroutines.user.Continuation;
import static com.offbynull.coroutines.user.Continuation.MODE_NORMAL;
import static com.offbynull.coroutines.user.Continuation.MODE_SAVING;
import com.offbynull.coroutines.user.MethodState;
import com.offbynull.coroutines.user.Instrumented;
import com.offbynull.coroutines.user.LockState;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
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
    private static final Method CONTINUATION_SUSPEND_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "suspend");
    private static final Method CONTINUATION_GETMODE_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "getMode");
    private static final Method CONTINUATION_SETMODE_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "setMode", Integer.TYPE);
    private static final Method CONTINUATION_ADDPENDING_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "addPending", MethodState.class);
    private static final Method CONTINUATION_REMOVELASTPENDING_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "removeLastPending");
    private static final Method CONTINUATION_REMOVEFIRSTSAVED_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "removeFirstSaved");
    private static final Constructor<MethodState> METHODSTATE_INIT_METHOD
            = ConstructorUtils.getAccessibleConstructor(MethodState.class, Integer.TYPE, Object[].class, Object[].class, LockState.class);
    private static final Method METHODSTATE_GETCONTINUATIONPOINT_METHOD
            = MethodUtils.getAccessibleMethod(MethodState.class, "getContinuationPoint");
    private static final Method METHODSTATE_GETLOCALTABLE_METHOD
            = MethodUtils.getAccessibleMethod(MethodState.class, "getLocalTable");
    private static final Method METHODSTATE_GETSTACK_METHOD
            = MethodUtils.getAccessibleMethod(MethodState.class, "getStack");
    private static final Method METHODSTATE_GETLOCKSTATE_METHOD
            = MethodUtils.getAccessibleMethod(MethodState.class, "getLockState");
    private static final Constructor<LockState> LOCKSTATE_INIT_METHOD
            = ConstructorUtils.getAccessibleConstructor(LockState.class);
    private static final Method LOCKSTATE_ENTER_METHOD
            = MethodUtils.getAccessibleMethod(LockState.class, "enter", Object.class);
    private static final Method LOCKSTATE_EXIT_METHOD
            = MethodUtils.getAccessibleMethod(LockState.class, "exit", Object.class);
    private static final Method LOCKSTATE_TOARRAY_METHOD
            = MethodUtils.getAccessibleMethod(LockState.class, "toArray");

    private ClassInformationRepository classRepo;

    /**
     * Constructs a {@link Instrumenter} object.
     * @param classpath classpath JARs and folders to use for instrumentation (this is needed by ASM to generate stack map frames).
     * @throws IOException if classes in the classpath could not be loaded up
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     */
    public Instrumenter(List<File> classpath) throws IOException {
        Validate.notNull(classpath);
        Validate.noNullElements(classpath);

        classRepo = ClassInformationRepository.create(classpath);
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
            List<AbstractInsnNode> saveInvocationInsnNodes
                    = findInvocationsWithParameter(methodNode.instructions, CONTINUATION_CLASS_TYPE);
            
            // If there are no continuation points, we don't need to instrument this method. It'll be like any other normal method
            // invocation because it won't have the potential to pause or call in to another method that may potentially pause.
            if (suspendInvocationInsnNodes.isEmpty() && saveInvocationInsnNodes.isEmpty()) {
                continue;
            }
            
            // Check for continuation points that use invokedynamic instruction, which are currently only used by lambdas. See comments in
            // validateNoInvokeDynamic to see why we need to do this.
            validateNoInvokeDynamic(suspendInvocationInsnNodes);
            validateNoInvokeDynamic(saveInvocationInsnNodes);
            
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
            Variable contArg = varTable.getArgument(contArgIdx);
            Variable methodStateVar = varTable.acquireExtra(MethodState.class);
            Variable savedLocalsVar = varTable.acquireExtra(Object[].class);
            Variable savedStackVar = varTable.acquireExtra(Object[].class);
            Variable tempObjVar = varTable.acquireExtra(Object.class);
            Variable lockStateVar = varTable.acquireExtra(LockState.class);
            Variable counterVar = varTable.acquireExtra(Type.INT_TYPE);
            Variable arrayLenVar = varTable.acquireExtra(Type.INT_TYPE);
                   
            // Generate code to deal with suspending around synchronized blocks
            MonitorInstrumentationLogic monitorInstrumentationLogic = generateMonitorInstrumentationLogic(
                    methodNode, tempObjVar, counterVar, arrayLenVar, lockStateVar, methodStateVar);
            
            // Generate code to deal with flow control (makes use of some of the code generated in monitorInstrumentationLogic)
            FlowInstrumentationLogic flowInstrumentationLogic = generateFlowInstrumentationLogic(methodNode, suspendInvocationInsnNodes,
                    saveInvocationInsnNodes, frames, monitorInstrumentationLogic, contArg, methodStateVar, savedLocalsVar, savedStackVar,
                    tempObjVar);
            
            // Apply generated code
            applyInstrumentationLogic(methodNode, flowInstrumentationLogic, monitorInstrumentationLogic);
        }

        // Write tree model back out as class
        ClassWriter cw = new SimpleClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES, classRepo);
        classNode.accept(cw);
        return cw.toByteArray();
    }
    
    private MonitorInstrumentationLogic generateMonitorInstrumentationLogic(MethodNode methodNode,
            Variable tempObjVar,
            Variable counterVar,
            Variable arrayLenVar,
            Variable lockStateVar,
            Variable methodStateVar) {

        
        // Find monitorenter/monitorexit and create replacement instructions that keep track of which objects were entered/exited.
        //
        //
        // Check for synchronized code blocks. Synchronized methods and Java 5 locks are okay to ignore, but synchronized blocks need to
        // be instrumented. For every MONITORENTER instruction that's performed on an object, there needs to be an equivalent MONITOREXIT
        // before the method exits. Otherwise, the method throws an IllegalMonitorState exception when it exits. I've confirmed by making a
        // simple java class in JASMIN assembler that did 10 MONITORENTERs in a loop and returned. That code threw
        // IllegalMonitorStateException when it tried to return. This link seems to explain it pretty well:
        // http://mail.openjdk.java.net/pipermail/hotspot-runtime-dev/2008-April/000118.html.
        //
        // So that means that if we're going to instrument the code to suspend and return in places, we need to make sure to exit all
        // monitors when we return and to re-enter those monitors when we come back in. We can't handle this via static analysis like we do
        // with inspecting the operand stack and local variables. It looks like static analysis is what Javaflow tries to do, which will
        // work in 99% of cases but won't in some edge cases like if the code was written in some JVM langauge other than Java.
        //
        // The following code creates replacements for every MONITORENTER and MONITOREXIT instruction such that those monitors get tracked
        // in a LockState object.
        List<AbstractInsnNode> monitorInsnNodes = searchForOpcodes(methodNode.instructions, Opcodes.MONITORENTER, Opcodes.MONITOREXIT);
        Map<AbstractInsnNode, InsnList> monitorInsnNodeReplacements = new HashMap<>();
        
        
        // IMPORTANT NOTE: The following code only generates code if monitorInsnNodes is NOT empty. That means that there has to be at least
        // one MONITORENTER or one MONITOREXIT for this method to generate instructions. Otherwise, all instruction listings will be stubbed
        // out with empty instruction lists.
        
        for (AbstractInsnNode monitorInsnNode : monitorInsnNodes) {
            InsnNode insnNode = (InsnNode) monitorInsnNode;
            InsnList replacementLogic;
            
            switch (insnNode.getOpcode()) {
                case Opcodes.MONITORENTER:
                    replacementLogic
                            = merge(
                                    // debugPrint("enter monitor"),
                                    saveVar(tempObjVar),
                                    loadVar(tempObjVar),
                                    cloneMonitorNode(insnNode),
                                    call(LOCKSTATE_ENTER_METHOD, loadVar(lockStateVar), loadVar(tempObjVar)) // track after entered
                            );
                    break;
                case Opcodes.MONITOREXIT:
                    replacementLogic
                            = merge(
                                    // debugPrint("exit monitor"),
                                    saveVar(tempObjVar),
                                    loadVar(tempObjVar),
                                    cloneMonitorNode(insnNode),
                                    call(LOCKSTATE_EXIT_METHOD, loadVar(lockStateVar), loadVar(tempObjVar)) // discard after exit
                            );
                    break;
                default:
                    throw new IllegalStateException(); // should never happen
            }
            
            monitorInsnNodeReplacements.put(monitorInsnNode, replacementLogic);
        }
        
        
        // Create code to create a new lockstate object
        InsnList createAndStoreLockStateInsnList;
        if (monitorInsnNodeReplacements.isEmpty()) { // if we don't have any MONITORENTER/MONITOREXIT, ignore this
            createAndStoreLockStateInsnList = empty();
        } else {
            createAndStoreLockStateInsnList
                    = merge(
                            construct(LOCKSTATE_INIT_METHOD),
                            saveVar(lockStateVar)
                    );
        }
        
        
        // Create code to load lockstate object from methodstate
        InsnList loadAndStoreLockStateFromMethodStateInsnList;
        if (monitorInsnNodeReplacements.isEmpty()) {
            loadAndStoreLockStateFromMethodStateInsnList = empty();
        } else {
            loadAndStoreLockStateFromMethodStateInsnList
                    = merge(
                            call(METHODSTATE_GETLOCKSTATE_METHOD, loadVar(methodStateVar)),
                            saveVar(lockStateVar)
                    );
        }

        
        // Create code to load lockstate object to the stack
        InsnList loadLockStateToStackInsnList;
        if (monitorInsnNodeReplacements.isEmpty()) {
            loadLockStateToStackInsnList = loadNull();
        } else {
            loadLockStateToStackInsnList = loadVar(lockStateVar);
        }

        
        // Create code to enter all monitors in lockstate object
        InsnList enterMonitorsInLockStateInsnList;
        if (monitorInsnNodeReplacements.isEmpty()) {
            enterMonitorsInLockStateInsnList = empty();
        } else {
            enterMonitorsInLockStateInsnList
                    = forEach(counterVar, arrayLenVar,
                            call(LOCKSTATE_TOARRAY_METHOD, loadVar(lockStateVar)),
                            merge(
                                    // debugPrint("temp monitor enter"),
                                    monitorEnter()
                            )
                    );
        }

        

        // Create code to exit all monitors in lockstate object
        InsnList exitMonitorsInLockStateInsnList;
        if (monitorInsnNodeReplacements.isEmpty()) {
            exitMonitorsInLockStateInsnList = empty();
        } else {
            exitMonitorsInLockStateInsnList
                    = forEach(counterVar, arrayLenVar,
                            call(LOCKSTATE_TOARRAY_METHOD, loadVar(lockStateVar)),
                                    merge(
                                    // debugPrint("temp monitor exit"),
                                    monitorExit()
                            )
                    );
        }
        
        return new MonitorInstrumentationLogic(monitorInsnNodeReplacements,
                createAndStoreLockStateInsnList,
                loadAndStoreLockStateFromMethodStateInsnList,
                loadLockStateToStackInsnList,
                enterMonitorsInLockStateInsnList,
                exitMonitorsInLockStateInsnList);
    }
    
    private FlowInstrumentationLogic generateFlowInstrumentationLogic(MethodNode methodNode,
            List<AbstractInsnNode> suspendInvocationInsnNodes,
            List<AbstractInsnNode> saveInvocationInsnNodes,
            Frame[] frames,
            MonitorInstrumentationLogic monitorInstrumentationLogic,
            Variable contArg,
            Variable methodStateVar,
            Variable savedLocalsVar,
            Variable savedStackVar,
            Variable tempObjVar) {

        // Get return type
        Type returnType = Type.getMethodType(methodNode.desc).getReturnType();

        // Generate instructions for continuation points
        int nextId = 0;
        List<ContinuationPoint> continuationPoints = new LinkedList<>();

        for (AbstractInsnNode suspendInvocationInsnNode : suspendInvocationInsnNodes) {
            int insnIdx = methodNode.instructions.indexOf(suspendInvocationInsnNode);
            ContinuationPoint cp = new ContinuationPoint(true, nextId, suspendInvocationInsnNode, frames[insnIdx]);
            continuationPoints.add(cp);
            nextId++;
        }

        for (AbstractInsnNode saveInvocationInsnNode : saveInvocationInsnNodes) {
            int insnIdx = methodNode.instructions.indexOf(saveInvocationInsnNode);
            ContinuationPoint cp = new ContinuationPoint(false, nextId, saveInvocationInsnNode, frames[insnIdx]);
            continuationPoints.add(cp);
            nextId++;
        }

        // IMPORTANT NOTE: Code dealing with locks (e.g. anything to do with LockState) will only be present if this method contains
        // MONITORENTER/MONITOREXIT. See comments in generateMonitorInstrumentationLogic for more information.
        
        // Generate entrypoint instructions...
        //
        //    LockState lockState;
        //    switch(continuation.getMode()) {
        //        case NORMAL:
        //        {
        //            lockState = new LockState();
        //            goto start;
        //        }
        //        case SAVING: throw exception
        //        case LOADING:
        //        {
        //            MethodState methodState = continuation.removeFirstSaved();
        //            Object[] stack = methodState.getStack();
        //            Object[] localVars = methodState.getLocalTable();
        //            lockState = methodState.getLockState();
        //            switch(methodState.getContinuationPoint()) {
        //                case <number>:
        //                    enterLocks(lockState);
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
        InsnList enterMonitorsInLockStateInsnList = monitorInstrumentationLogic.getEnterMonitorsInLockStateInsnList();
        LabelNode startOfMethodLabelNode = new LabelNode();
        InsnList entryPointInsnList
                = merge(
                        tableSwitch(
                                call(CONTINUATION_GETMODE_METHOD, loadVar(contArg)),
                                throwException("Unrecognized state"),
                                0,
                                merge(
                                        // debugPrint("fresh invoke" + methodNode.name),
                                        monitorInstrumentationLogic.getCreateAndStoreLockStateInsnList(),
                                        jumpTo(startOfMethodLabelNode)
                                ),
                                throwException("Unexpected state (saving not allowed at this point)"),
                                merge(
                                        // debugPrint("calling remove first saved" + methodNode.name),
                                        call(CONTINUATION_REMOVEFIRSTSAVED_METHOD, loadVar(contArg)),
                                        saveVar(methodStateVar),
                                        call(METHODSTATE_GETLOCALTABLE_METHOD, loadVar(methodStateVar)),
                                        saveVar(savedLocalsVar),
                                        call(METHODSTATE_GETSTACK_METHOD, loadVar(methodStateVar)),
                                        saveVar(savedStackVar),
                                        monitorInstrumentationLogic.getLoadAndStoreLockStateFromMethodStateInsnList(),
                                        tableSwitch(
                                                call(METHODSTATE_GETCONTINUATIONPOINT_METHOD, loadVar(methodStateVar)),
                                                throwException("Unrecognized restore id" + methodNode.name),
                                                0,
                                                continuationPoints.stream().map((cp) -> {
                                                    InsnList ret
                                                            = merge(
                                                                                                      // inserted many times, must be cloned
                                                                    cloneInsnList(enterMonitorsInLockStateInsnList), 
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
        

        // IMPORTANT NOTE: Code dealing with locks (e.g. anything to do with LockState) will only be present if this method contains
        // MONITORENTER/MONITOREXIT. See comments in generateMonitorInstrumentationLogic for more information.
        
        // Generates store logic and restore addLabel for each continuation point
        //
        //      #IFDEF suspend
        //          Object[] stack = saveOperandStack();
        //          Object[] locals = saveLocalsStackHere();
        //          continuation.addPending(new MethodState(<number>, stack, locals, lockState);
        //          continuation.setMode(MODE_SAVING);
        //          exitLocks(lockState);
        //          return <dummy>;
        //          restorePoint_<number>:
        //          continuation.setMode(MODE_NORMAL);
        //      #ENDIF
        //
        //      #IFDEF !suspend
        //          restorePoint_<number>:
        //          Object[] stack = saveOperandStack();
        //          Object[] locals = saveLocalsStackHere();
        //          continuation.addPending(new MethodState(<number>, stack, locals, lockState);
        //          <method invocation>
        //          if (continuation.getMode() == MODE_SAVING) {
        //              exitLocks(lockState);
        //              return <dummy>;
        //          }
        //          continuation.removeLastPending();
        //      #ENDIF
        InsnList loadLockStateToStackInsnList = monitorInstrumentationLogic.getLoadLockStateToStackInsnList();
        Map<AbstractInsnNode, InsnList> invokeInsnNodeReplacements = new HashMap<>();
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
                                            loadVar(savedLocalsVar),
                                            cloneInsnList(loadLockStateToStackInsnList) // inserted many times, must be cloned
                                    )
                            )
                    );

            InsnList exitMonitorsInLockStateInsnList = monitorInstrumentationLogic.getExitMonitorsInLockStateInsnList();
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
                                // debugPrint("exiting monitors" + methodNode.name),
                                cloneInsnList(exitMonitorsInLockStateInsnList), // used several times, must be cloned
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
                                        merge(
                                                // debugPrint("exiting monitors" + methodNode.name),
                                                cloneInsnList(exitMonitorsInLockStateInsnList), // inserted many times, must be cloned
                                                // debugPrint("returning dummy value" + methodNode.name),
                                                returnDummy(returnType)
                                        )
                                ),
                                // debugPrint("calling remove last pending" + methodNode.name),
                                call(CONTINUATION_REMOVELASTPENDING_METHOD, loadVar(contArg)) // otherwise assume we're normal, and
                                                                                              // remove the state we added on to
                                                                                              // pending
                        );
            }

            invokeInsnNodeReplacements.put(cp.getInvokeInsnNode(), insnList);
        });
        
        // We don't want labels to continuationPoints to be remapped when FlowInstrumentationLogic returns them
        return new FlowInstrumentationLogic(entryPointInsnList, invokeInsnNodeReplacements);
    }
    
    private void applyInstrumentationLogic(MethodNode methodNode,
            FlowInstrumentationLogic flowInstrumentationLogic,
            MonitorInstrumentationLogic monitorInstrumentationLogic) {
        
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
