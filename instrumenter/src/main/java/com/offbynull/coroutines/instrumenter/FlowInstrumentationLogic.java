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
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadVar;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.merge;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.pop;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.returnDummy;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.saveLocalVariableTable;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.saveOperandStack;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.saveVar;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.tableSwitch;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.throwException;
import com.offbynull.coroutines.instrumenter.asm.VariableTable;
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
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

final class FlowInstrumentationLogic {
    
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
    
    private final InsnList entryPointInsnList;
    private final Map<AbstractInsnNode, InsnList> invokeInsnNodeReplacements;

    static FlowInstrumentationLogic generate(MethodNode methodNode,
            List<AbstractInsnNode> suspendInvocationInsnNodes,
            List<AbstractInsnNode> saveInvocationInsnNodes,
            Frame<BasicValue>[] frames,
            MonitorInstrumentationLogic monitorInstrumentationLogic,
            FlowInstrumentationVariables flowInstrumentationVariables) {

        VariableTable.Variable contArg = flowInstrumentationVariables.getContArg();
        VariableTable.Variable pendingCountVar = flowInstrumentationVariables.getPendingCountVar();
        VariableTable.Variable methodStateVar = flowInstrumentationVariables.getMethodStateVar();
        VariableTable.Variable savedLocalsVar = flowInstrumentationVariables.getSavedLocalsVar();
        VariableTable.Variable savedStackVar = flowInstrumentationVariables.getSavedStackVar();
        VariableTable.Variable tempObjVar = flowInstrumentationVariables.getTempObjectVar();

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
        //    int pendingCount = continuation.getPendingSize();
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
                        // debugPrint("calling get pending size" + methodNode.name),
                        call(CONTINUATION_GETPENDINGSIZE_METHOD, loadVar(contArg)),
                        saveVar(pendingCountVar),
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
                                                throwException("Unrecognized restore id " + methodNode.name),
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
        //      // Clear any excess pending MethodStates that may be lingering. We need to do this because we may have pending method states
        //      // sitting around from methods that threw an exception. When a method that takes in a Continuation throws an exception it
        //      // means that that method won't clear out its pending method state.
        //      continuation.clearExcessPending(pendingCount);
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
            InsnList clearExcessPendingInsnList
                    = merge(
                            //debugPrint("clearing potential excess" + methodNode.name),
                            call(CONTINUATION_CLEAREXCESSPENDING_METHOD, loadVar(contArg), loadVar(pendingCountVar))
                    );
            
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
                                clearExcessPendingInsnList, // clear excess pendingstates
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
                                clearExcessPendingInsnList, // clear excess pendingstates
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
        
    private FlowInstrumentationLogic(InsnList entryPointInsnList, Map<AbstractInsnNode, InsnList> invokeInsnNodeReplacements) {
        this.entryPointInsnList = entryPointInsnList;
        this.invokeInsnNodeReplacements = invokeInsnNodeReplacements;
    }
    
    // WARNING: Be careful with using these more than once. If you insert one InsnList in to another InsnList, it'll become empty. If you
    // need to insert the instructions in an InsnList multiple times, make sure to CLONE IT FIRST!
    
    InsnList getEntryPointInsnList() {
        return entryPointInsnList;
    }

    Map<AbstractInsnNode, InsnList> getInvokeInsnNodeReplacements() {
        return invokeInsnNodeReplacements;
    }
}
