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

import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.addLabel;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.call;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.cloneInsnList;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.cloneInvokeNode;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.construct;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.ifIntegersEqual;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.jumpTo;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadIntConst;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadLocalVariableTable;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadOperandStack;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadOperandStackPrefix;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadOperandStackSuffix;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadVar;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.merge;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.pop;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.returnDummy;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.saveLocalVariableTable;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.saveOperandStack;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.saveVar;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.tableSwitch;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.throwException;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.getRequiredStackCountForInvocation;
import com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable;
import com.offbynull.coroutines.user.Continuation;
import static com.offbynull.coroutines.user.Continuation.MODE_NORMAL;
import static com.offbynull.coroutines.user.Continuation.MODE_SAVING;
import com.offbynull.coroutines.user.LockState;
import com.offbynull.coroutines.user.MethodState;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

final class FlowInstrumentationGenerator {
    
    private static final Method CONTINUATION_GETMODE_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "getMode");
    private static final Method CONTINUATION_SETMODE_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "setMode", Integer.TYPE);
    private static final Method CONTINUATION_GETPENDINGSIZE_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "getPendingSize");
    private static final Method CONTINUATION_CLEAREXCESSPENDING_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "clearExcessPending", Integer.TYPE);
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
    
    private final MethodNode methodNode;
    private final List<AbstractInsnNode> suspendInvocationInsnNodes;
    private final List<AbstractInsnNode> saveInvocationInsnNodes;
    private final Frame<BasicValue>[] frames;

    private final Variable contArg;
    private final Variable pendingCountVar;
    private final Variable methodStateVar;
    private final Variable savedLocalsVar;
    private final Variable savedStackVar;
    private final Variable tempObjVar;
    
    private final InsnList createAndStoreLockStateInsnList;
    private final InsnList loadLockStateToStackInsnList;
    private final InsnList enterMonitorsInLockStateInsnList;
    private final InsnList exitMonitorsInLockStateInsnList;
    private final InsnList loadAndStoreLockStateFromMethodStateInsnList;

    FlowInstrumentationGenerator(MethodNode methodNode, List<AbstractInsnNode> suspendInvocationInsnNodes,
            List<AbstractInsnNode> saveInvocationInsnNodes, Frame<BasicValue>[] frames,
            MonitorInstrumentationInstructions monitorInstrumentationLogic, FlowInstrumentationVariables flowInstrumentationVariables) {
        Validate.notNull(methodNode);
        Validate.notNull(suspendInvocationInsnNodes);
        Validate.notNull(saveInvocationInsnNodes);
        Validate.notNull(saveInvocationInsnNodes);
        Validate.notNull(frames);
        Validate.notNull(monitorInstrumentationLogic);
        Validate.notNull(flowInstrumentationVariables);
        Validate.noNullElements(suspendInvocationInsnNodes);
        Validate.noNullElements(saveInvocationInsnNodes);
        //Validate.noNullElements(frames); // frames can have null elements
        
        this.methodNode = methodNode;
        this.suspendInvocationInsnNodes = suspendInvocationInsnNodes;
        this.saveInvocationInsnNodes = saveInvocationInsnNodes;
        this.frames = frames;

        contArg = flowInstrumentationVariables.getContArg();
        pendingCountVar = flowInstrumentationVariables.getPendingCountVar();
        methodStateVar = flowInstrumentationVariables.getMethodStateVar();
        savedLocalsVar = flowInstrumentationVariables.getSavedLocalsVar();
        savedStackVar = flowInstrumentationVariables.getSavedStackVar();
        tempObjVar = flowInstrumentationVariables.getTempObjectVar();
        
        createAndStoreLockStateInsnList = monitorInstrumentationLogic.getCreateAndStoreLockStateInsnList();
        loadLockStateToStackInsnList = monitorInstrumentationLogic.getLoadLockStateToStackInsnList();
        enterMonitorsInLockStateInsnList = monitorInstrumentationLogic.getEnterMonitorsInLockStateInsnList();
        exitMonitorsInLockStateInsnList = monitorInstrumentationLogic.getExitMonitorsInLockStateInsnList();
        loadAndStoreLockStateFromMethodStateInsnList = monitorInstrumentationLogic.getLoadAndStoreLockStateFromMethodStateInsnList();
    }

    FlowInstrumentationInstructions generate() {
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
        //    MethodState methodState;
        //    Object[] stack;
        //    Object[] localVars;
        //    int pendingCount = continuation.getPendingSize();
        //
        //    switch(continuation.getMode()) {
        //        case NORMAL:
        //        {
        //            lockState = new LockState();
        //            goto start;
        //        }
        //        case SAVING: throw exception
        //        case LOADING:
        //        {
        //            methodState = continuation.removeFirstSaved();
        //            stack = methodState.getStack();
        //            localVars = methodState.getLocalTable();
        //            lockState = methodState.getLockState();
        //            switch(methodState.getContinuationPoint()) {
        //                case <number>:
        //                    goto restorePoint_<number>_loadExecute;
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
                        // debugPrint("calling get pending size" + methodNode.name),
                        call(CONTINUATION_GETPENDINGSIZE_METHOD, loadVar(contArg)),
                        saveVar(pendingCountVar),
                        tableSwitch(
                                call(CONTINUATION_GETMODE_METHOD, loadVar(contArg)),
                                throwException("Unrecognized state"),
                                0,
                                merge(
                                        // debugPrint("fresh invoke" + methodNode.name),
                                        createAndStoreLockStateInsnList,
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
                                        loadAndStoreLockStateFromMethodStateInsnList,
                                        tableSwitch(
                                                call(METHODSTATE_GETCONTINUATIONPOINT_METHOD, loadVar(methodStateVar)),
                                                throwException("Unrecognized restore id " + methodNode.name),
                                                0,
                                                continuationPoints.stream().map((cp) -> {
                                                    // WE MUST RE-ENTER MONITORS HERE! Because the entire method will implicitly be wrapped
                                                    // in a try-catch-finally block for handling exiting monitors if the method throws an
                                                    // exception
                                                    return merge(
                                                            // debugPrint("entering monitors" + methodNode.name),
                                                            cloneInsnList(enterMonitorsInLockStateInsnList),
                                                            jumpTo(cp.getRestoreLabelNode())
                                                    );
                                                }).toArray((x) -> new InsnList[x])
                                        )
                                // jump to not required here, switch above either throws exception or jumps to restore point
                                )
                        ),
                        addLabel(startOfMethodLabelNode)
                );
        

        // IMPORTANT NOTE: Code dealing with locks (e.g. anything to do with LockState) will only be present if this method contains
        // MONITORENTER/MONITOREXIT. See comments in monitor instrumentation code for more information.
        
        // Generates store logic and restore addLabel for each continuation point
        Map<AbstractInsnNode, InsnList> invokeInsnNodeReplacements = new HashMap<>();
        continuationPoints.forEach((cp) -> {
            InsnList insnList;
            if (cp.isSuspend()) {
                insnList = generateSuspendContinuationPointInstructions(cp, returnType);
            } else {
                int methodStackCount = getRequiredStackCountForInvocation(cp.getInvokeInsnNode());
                insnList = generateInvokeContinuationPointInstructions(cp, returnType, methodStackCount);
            }


            invokeInsnNodeReplacements.put(cp.getInvokeInsnNode(), insnList);
        });
        
        // We don't want labels to continuationPoints to be remapped when FlowInstrumentationInstructions returns them
        return new FlowInstrumentationInstructions(entryPointInsnList, invokeInsnNodeReplacements);
    }
    
    private InsnList generateInvokeContinuationPointInstructions(ContinuationPoint cp, Type returnType, int methodStackCount) {
        //          restorePoint_<number>_normalExecute: // at this label: normal exec stack / normal exec var table
        //             // Clear any excess pending MethodStates that may be lingering. We need to do this because we may have pending method
        //             // states sitting around from methods that threw an exception. When a method that takes in a Continuation throws an
        //             // exception it means that that method won't clear out its pending method state.
        //          continuation.clearExcessPending(pendingCount);
        //          Object[] stack = saveOperandStack();
        //          Object[] locals = saveLocals();
        //          continuation.addPending(new MethodState(<number>, stack, locals, lockState);
        //          <method invocation>
        //          if (continuation.getMode() == MODE_SAVING) {
        //              exitLocks(lockState);
        //              return <dummy>;
        //          }
        //          continuation.removeLastPending();
        //          goto restorePoint_<number>_end;
        //
        //          restorePoint_<number>_loadExecute: // at this label: empty exec stack / uninit exec var table
        //          exitLocks(lockState);
        //          continuation.addPending(methodState); // method state should be loaded from Continuation.saved
        //          restoreStackSuffix(stack, <number of items required for method invocation below>);
        //          <method invocation>
        //          if (continuation.getMode() == MODE_SAVING) {
        //              exitLocks(lockState);
        //              return <dummy>;
        //          }
        //          restoreOperandStack(stack);
        //          restoreLocalsStack(localVars);
        //
        //          restorePoint_<number>_end:
        return merge(
                addLabel(cp.getNormalLabelNode()),
                // debugPrint("clear excess pending" + methodNode.name),
                call(CONTINUATION_CLEAREXCESSPENDING_METHOD, loadVar(contArg), loadVar(pendingCountVar)),
                // debugPrint("saving operand stack" + methodNode.name),
                saveOperandStack(savedStackVar, tempObjVar, cp.getFrame()),
                // debugPrint("saving locals" + methodNode.name),
                saveLocalVariableTable(savedLocalsVar, tempObjVar, cp.getFrame()),
                // debugPrint("calling addPending" + methodNode.name),
                call(CONTINUATION_ADDPENDING_METHOD, loadVar(contArg),
                        construct(METHODSTATE_INIT_METHOD,
                                loadIntConst(cp.getId()),
                                loadVar(savedStackVar),
                                loadVar(savedLocalsVar),
                                cloneInsnList(loadLockStateToStackInsnList) // inserted many times, must be cloned
                        )
                ),
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
                call(CONTINUATION_REMOVELASTPENDING_METHOD, loadVar(contArg)), // otherwise assume we're normal, and
                                                                               // remove the state we added on to
                                                                               // pending
                // debugPrint("jumping to end" + methodNode.name),
                jumpTo(cp.getEndLabelNode()),
                
                
                addLabel(cp.getRestoreLabelNode()),
                // RESTORING CODE NEEDS TO GO IN TO SWITCH STATEMENT THAT CURRENTLY JUMPS HERE. THIS IS BECAUSE OF SYNCHRONIZED BLOCKS. THE
                // METHOD IS WRAPPED IN A GIANT TRY-CATCH-FINALLY WHERE MONITOREXITS ARE PERFORMED ON LOCAL VARIABLES; THOSE MONITOREXITS CAN'T
                // BE EXECUTED IF THE LOCAL VARIABLE TABLE HASN'T INITALIZED THE OBJECTS THEY'RE SUPPOSED TO BE CALLED ON (VERIFIER WONT EVEN
                // LET THE CODE THROUGH). WE JUMP HERE FROM THE SWITCH WITH ESSENTIALLY A BLANK LOCAL VARIABLES TABLE AND OPERAND STACK.
                // AN EXCEPTION CAN OCCUR BEFORE THE LOCAL VARIABLES TABLE HAS BEEN RECONSTRUCTED. AS SUCH, MONITOREXITS MAY BE REFERRING TO
                // EMPTY SLOTS. WHICH IS WHY WE NEED TO DO THIS CODE IN THE SWITCH.
                MOVERESTORECODETOSWITCHBEINGADDEDATTOPOFMETHOD,
                // debugPrint("calling addPending" + methodNode.name),
                call(CONTINUATION_ADDPENDING_METHOD, loadVar(contArg), loadVar(methodStateVar)),
                // debugPrint("loading portion of operand stack required to invoke continuation point method (last "
                //        + methodStackCount + " items off operand stack are loaded on to stack)" + methodNode.name),
                loadOperandStackSuffix(savedStackVar, tempObjVar, cp.getFrame(), methodStackCount),
                // debugPrint("invoking with loaded items " + methodNode.name),
                cloneInvokeNode(cp.getInvokeInsnNode()), // invoke method
                POPRESULT_AND_SAVE(IFANY),
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
                // debugPrint("loading portion of operand stack that skips items required to invoke continuation point method (last "
                //        + methodStackCount + " items off operand stack are NOT loaded on to stack)" + methodNode.name),
                loadOperandStackPrefix(savedStackVar, tempObjVar, cp.getFrame(), cp.getFrame().getStackSize() - methodStackCount),
                // debugPrint("loading locals" + methodNode.name),
                loadLocalVariableTable(savedLocalsVar, tempObjVar, cp.getFrame()),
                // debugPrint("restored" + methodNode.name),
                LOAD_AND_PUSHRESULT(IFANY)
                
                
                addLabel(cp.getEndLabelNode())
        );
    }
    
    private InsnList generateSuspendContinuationPointInstructions(ContinuationPoint cp, Type returnType) {
        //          restorePoint_<number>_normalExecute: // at this label: normal exec stack / normal exec var table
        //             // Clear any excess pending MethodStates that may be lingering. We need to do this because we may have pending method
        //             // states sitting around from methods that threw an exception. When a method that takes in a Continuation throws an
        //             // exception it means that that method won't clear out its pending method state.
        //          continuation.clearExcessPending(pendingCount);
        //          Object[] stack = saveOperandStack();
        //          Object[] locals = saveLocals();
        //          continuation.addPending(new MethodState(<number>, stack, locals, lockState);
        //          continuation.setMode(MODE_SAVING);
        //          exitLocks(lockState);
        //          return <dummy>;
        //
        //          restorePoint_<number>_loadExecute: // at this label: empty exec stack / uninit exec var table
        //          enterLocks(lockState);
        //          restoreOperandStack(stack);
        //          restoreLocalsStack(localVars);
        //          continuation.setMode(MODE_NORMAL);
        //
        //          restorePoint_<number>_end:
        return merge(
                addLabel(cp.getNormalLabelNode()),
                // debugPrint("clear excess pending" + methodNode.name),
                call(CONTINUATION_CLEAREXCESSPENDING_METHOD, loadVar(contArg), loadVar(pendingCountVar)),
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
                ),
                call(CONTINUATION_SETMODE_METHOD, loadVar(contArg), loadIntConst(MODE_SAVING)),
                // debugPrint("exiting monitors" + methodNode.name),
                cloneInsnList(exitMonitorsInLockStateInsnList), // used several times, must be cloned
                // debugPrint("returning dummy value" + methodNode.name),
                returnDummy(returnType), // return dummy value
                jumpTo(cp.getEndLabelNode()),
                
                
                addLabel(cp.getRestoreLabelNode()),
                // RESTORING CODE NEEDS TO GO IN TO SWITCH STATEMENT THAT CURRENTLY JUMPS HERE. THIS IS BECAUSE OF SYNCHRONIZED BLOCKS. THE
                // METHOD IS WRAPPED IN A GIANT TRY-CATCH-FINALLY WHERE MONITOREXITS ARE PERFORMED ON LOCAL VARIABLES; THOSE MONITOREXITS CAN'T
                // BE EXECUTED IF THE LOCAL VARIABLE TABLE HASN'T INITALIZED THE OBJECTS THEY'RE SUPPOSED TO BE CALLED ON (VERIFIER WONT EVEN
                // LET THE CODE THROUGH). WE JUMP HERE FROM THE SWITCH WITH ESSENTIALLY A BLANK LOCAL VARIABLES TABLE AND OPERAND STACK.
                // AN EXCEPTION CAN OCCUR BEFORE THE LOCAL VARIABLES TABLE HAS BEEN RECONSTRUCTED. AS SUCH, MONITOREXITS MAY BE REFERRING TO
                // EMPTY SLOTS. WHICH IS WHY WE NEED TO DO THIS CODE IN THE SWITCH.
                MOVERESTORECODETOSWITCHBEINGADDEDATTOPOFMETHOD,
                // debugPrint("loading operand stack" + methodNode.name),
                loadOperandStack(savedStackVar, tempObjVar, cp.getFrame()),
                // debugPrint("loading locals" + methodNode.name),
                loadLocalVariableTable(savedLocalsVar, tempObjVar, cp.getFrame()),
                // debugPrint("popping continuation ref off stack" + methodNode.name),
                pop(), // frame at the time of invocation to Continuation.suspend() has Continuation reference on the
                       // stack that would have been consumed by that invocation... since we're removing that call, we
                       // also need to pop the Continuation reference from the stack... it's important that we
                       // explicitly do it at this point becuase during loading the stack will be restored with top
                       // of stack pointing to that continuation object
                // debugPrint("going back in to normal mode" + methodNode.name),
                call(CONTINUATION_SETMODE_METHOD, loadVar(contArg), loadIntConst(MODE_NORMAL)),
                // debugPrint("restored" + methodNode.name),
                
                
                addLabel(cp.getEndLabelNode())
        );
    }
}
