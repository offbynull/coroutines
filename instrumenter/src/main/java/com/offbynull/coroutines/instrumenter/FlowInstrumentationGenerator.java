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
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.jumpTo;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadVar;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.merge;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.saveVar;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.tableSwitch;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.throwException;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.findLineNumberForInstruction;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.findTryCatchBlockNodesEncompassingInstruction;
import com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable;
import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.MethodState;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

final class FlowInstrumentationGenerator {
    
    private static final Method CONTINUATION_GETMODE_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "getMode");
    private static final Method CONTINUATION_GETPENDINGSIZE_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "getPendingSize");
    private static final Method CONTINUATION_REMOVEFIRSTSAVED_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "removeFirstSaved");
    private static final Method METHODSTATE_GETCONTINUATIONPOINT_METHOD
            = MethodUtils.getAccessibleMethod(MethodState.class, "getContinuationPoint");
    private static final Method METHODSTATE_GETLOCALTABLE_METHOD
            = MethodUtils.getAccessibleMethod(MethodState.class, "getLocalTable");
    private static final Method METHODSTATE_GETSTACK_METHOD
            = MethodUtils.getAccessibleMethod(MethodState.class, "getStack");
    
    private final MethodNode methodNode;
    private final List<AbstractInsnNode> suspendInvocationInsnNodes;
    private final List<AbstractInsnNode> invokeInvocationInsnNodes;
    private final Frame<BasicValue>[] frames;

    private final MonitorInstrumentationInstructions monitorInstrumentationInstructions;
    private final FlowInstrumentationVariables flowInstrumentationVariables;

    FlowInstrumentationGenerator(MethodNode methodNode, List<AbstractInsnNode> suspendInvocationInsnNodes,
            List<AbstractInsnNode> invokeInvocationInsnNodes, Frame<BasicValue>[] frames,
            MonitorInstrumentationInstructions monitorInstrumentationInstructions,
            FlowInstrumentationVariables flowInstrumentationVariables) {
        Validate.notNull(methodNode);
        Validate.notNull(suspendInvocationInsnNodes);
        Validate.notNull(invokeInvocationInsnNodes);
        Validate.notNull(invokeInvocationInsnNodes);
        Validate.notNull(frames);
        Validate.notNull(monitorInstrumentationInstructions);
        Validate.notNull(flowInstrumentationVariables);
        Validate.noNullElements(suspendInvocationInsnNodes);
        Validate.noNullElements(invokeInvocationInsnNodes);
        //Validate.noNullElements(frames); // frames can have null elements
        
        this.methodNode = methodNode;
        this.suspendInvocationInsnNodes = suspendInvocationInsnNodes;
        this.invokeInvocationInsnNodes = invokeInvocationInsnNodes;
        this.frames = frames;

        this.monitorInstrumentationInstructions = monitorInstrumentationInstructions;
        this.flowInstrumentationVariables = flowInstrumentationVariables;
    }

    FlowInstrumentationInstructions generate() {
        // Get return type
        Type returnType = Type.getMethodType(methodNode.desc).getReturnType();

        // Generate continuation point details
        int nextId = 0;
        List<ContinuationPointInstructions> continuationPoints = new LinkedList<>();

          // IMPORTANT NOTE: Code dealing with locks (e.g. anything to do with LockState) will only be present if this method contains
          // MONITORENTER/MONITOREXIT. See comments MonitorInstructionGenerator for more information.
        
        for (AbstractInsnNode suspendInvocationInsnNode : suspendInvocationInsnNodes) {
            int insnIdx = methodNode.instructions.indexOf(suspendInvocationInsnNode);
            LineNumberNode invokeLineNumberNode = findLineNumberForInstruction(methodNode.instructions, suspendInvocationInsnNode);
            ContinuationPointInstructions cp = new SuspendContinuationPointGenerator(
                    nextId,
                    suspendInvocationInsnNode,
                    invokeLineNumberNode,
                    frames[insnIdx],
                    returnType,
                    flowInstrumentationVariables,
                    monitorInstrumentationInstructions)
                    .generate();
            continuationPoints.add(cp);
            nextId++;
        }

        for (AbstractInsnNode invokeInvocationInsnNode : invokeInvocationInsnNodes) {
            boolean withinTryCatch = findTryCatchBlockNodesEncompassingInstruction(
                    methodNode.instructions,
                    methodNode.tryCatchBlocks,
                    invokeInvocationInsnNode).size() > 0;
            LineNumberNode invokeLineNumberNode = findLineNumberForInstruction(methodNode.instructions, invokeInvocationInsnNode);
            
            int insnIdx = methodNode.instructions.indexOf(invokeInvocationInsnNode);
            ContinuationPointInstructions cp;
            if (withinTryCatch) {
                cp = new InvokeWithinTryCatchContinuationPointGenerator(
                        nextId,
                        invokeInvocationInsnNode,
                        invokeLineNumberNode,
                        frames[insnIdx],
                        returnType,
                        flowInstrumentationVariables,
                        monitorInstrumentationInstructions)
                        .generate();
            } else {
                cp = new InvokeContinuationPointGenerator(
                        nextId,
                        invokeInvocationInsnNode,
                        invokeLineNumberNode,
                        frames[insnIdx],
                        returnType,
                        flowInstrumentationVariables,
                        monitorInstrumentationInstructions)
                        .generate();
            }
            
            continuationPoints.add(cp);
            nextId++;
        }
        
        Variable contArg = flowInstrumentationVariables.getContArg();
        Variable pendingCountVar = flowInstrumentationVariables.getPendingCountVar();
        Variable methodStateVar = flowInstrumentationVariables.getMethodStateVar();
        Variable savedLocalsVar = flowInstrumentationVariables.getSavedLocalsVar();
        Variable savedStackVar = flowInstrumentationVariables.getSavedStackVar();
        
        InsnList createAndStoreLockStateInsnList
                = monitorInstrumentationInstructions.getCreateAndStoreLockStateInsnList();
        InsnList loadAndStoreLockStateFromMethodStateInsnList
                = monitorInstrumentationInstructions.getLoadAndStoreLockStateFromMethodStateInsnList();
        
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
        //                    <CP_RESTORE_INSTRUCTIONS>
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
                        call(CONTINUATION_GETPENDINGSIZE_METHOD, loadVar(contArg)), // call getPendingSize()
                        saveVar(pendingCountVar),
                        tableSwitch(
                                call(CONTINUATION_GETMODE_METHOD, loadVar(contArg)),
                                throwException("Unrecognized state"),
                                0,
                                merge(
                                        // fresh invoke
                                        createAndStoreLockStateInsnList,
                                        jumpTo(startOfMethodLabelNode)
                                ),
                                throwException("Unexpected state (saving not allowed at this point)"),
                                merge(
                                        // restore invoke
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
                                                continuationPoints.stream()
                                                        .map((cp) -> cp.getRestoreInsnNodes())
                                                        .toArray((x) -> new InsnList[x])
                                        )
                                        // jump to not required here, switch above either throws exception or jumps to restore point
                                )
                        ),
                        addLabel(startOfMethodLabelNode)
                );

        
        
        // Generates invoke replacement logic and new trycatchblocks requires
        List<TryCatchBlockNode> addedTryCatchBlockNodes = new ArrayList<>(continuationPoints.size());
        Map<AbstractInsnNode, InsnList> invokeInsnNodeReplacements = new HashMap<>();
        continuationPoints.forEach((cp) -> {
            invokeInsnNodeReplacements.put(cp.getOriginalInvokeInsnNode(), cp.getInvokeReplacementInsnNodes());
            addedTryCatchBlockNodes.addAll(cp.getTryCatchBlockNodes());
        });
        
        // We don't want labels to continuationPoints to be remapped when FlowInstrumentationInstructions returns them
        return new FlowInstrumentationInstructions(entryPointInsnList, invokeInsnNodeReplacements, addedTryCatchBlockNodes);
    }
}
