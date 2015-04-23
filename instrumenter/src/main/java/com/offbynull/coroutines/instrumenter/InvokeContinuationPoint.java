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

import static com.offbynull.coroutines.instrumenter.ContinuationPointInstructionUtils.castToObjectAndSave;
import static com.offbynull.coroutines.instrumenter.ContinuationPointInstructionUtils.loadAndCastToOriginal;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.addLabel;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.call;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.cloneInsnList;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.cloneInvokeNode;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.construct;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.empty;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.ifIntegersEqual;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.jumpTo;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.lineNumber;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadIntConst;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadLocalVariableTable;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadOperandStackPrefix;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadOperandStackSuffix;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadVar;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.merge;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.returnDummy;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.saveLocalVariableTable;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.saveOperandStack;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.getRequiredStackCountForInvocation;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.getReturnTypeOfInvocation;
import com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable;
import static com.offbynull.coroutines.user.Continuation.MODE_SAVING;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

final class InvokeContinuationPoint extends ContinuationPoint {
    
    private final LabelNode continueExecLabelNode = new LabelNode();

    public InvokeContinuationPoint(int id, AbstractInsnNode invokeInsnNode, LineNumberNode invokeLineNumberNode, Frame<BasicValue> frame,
            Type returnType,
            FlowInstrumentationVariables flowInstrumentationVariables,
            MonitorInstrumentationInstructions monitorInstrumentationInstructions) {
        super(id, invokeInsnNode, invokeLineNumberNode, frame, returnType, flowInstrumentationVariables,
                monitorInstrumentationInstructions);
    }
    
    @Override
    InsnList generateLoadInstructions() {
        FlowInstrumentationVariables vars = getFlowInstrumentationVariables();
        MonitorInstrumentationInstructions monInsts = getMonitorInstrumentationInstructions();
        
        Variable contArg = vars.getContArg();
        Variable methodStateVar = vars.getMethodStateVar();
        Variable savedLocalsVar = vars.getSavedLocalsVar();
        Variable savedStackVar = vars.getSavedStackVar();
        Variable tempObjVar = vars.getTempObjectVar();
        Variable tempObjVar2 = vars.getTempObjVar2();
        
        InsnList enterMonitorsInLockStateInsnList = monInsts.getEnterMonitorsInLockStateInsnList();
        InsnList exitMonitorsInLockStateInsnList = monInsts.getExitMonitorsInLockStateInsnList();
        
        Type invokeMethodReturnType = getReturnTypeOfInvocation(getInvokeInsnNode());        
        Type returnType = getReturnType();
        Integer lineNum = getLineNumber();
        int methodStackCount = getRequiredStackCountForInvocation(getInvokeInsnNode());
        
        Frame<BasicValue> frame = getFrame();
        
        return merge(lineNum == null ? empty() : lineNumber(lineNum),
                // debugPrint("restoring " + methodNode.name),
                // debugPrint("entering monitors" + methodNode.name),
                cloneInsnList(enterMonitorsInLockStateInsnList),
                // debugPrint("calling addPending" + methodNode.name),
                call(CONTINUATION_ADDPENDING_METHOD, loadVar(contArg), loadVar(methodStateVar)),
                // debugPrint("loading portion of operand stack required to invoke continuation point method (last "
                //        + methodStackCount + " items off operand stack are loaded on to stack)" + methodNode.name),
                loadOperandStackSuffix(savedStackVar, tempObjVar, frame, methodStackCount),
                // debugPrint("invoking with loaded items " + methodNode.name),
                cloneInvokeNode(getInvokeInsnNode()), // invoke method  
                                // debugPrint("saving return type of invocation" + methodNode.name),
                castToObjectAndSave(invokeMethodReturnType, tempObjVar2), // save return (does nothing if void)
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
                loadOperandStackPrefix(savedStackVar, tempObjVar, frame, frame.getStackSize() - methodStackCount),
                // debugPrint("loading locals" + methodNode.name),
                loadLocalVariableTable(savedLocalsVar, tempObjVar, frame),
                // debugPrint("loading return type of invocation" + methodNode.name),
                loadAndCastToOriginal(invokeMethodReturnType, tempObjVar2),
                // debugPrint("restored" + methodNode.name),
                jumpTo(continueExecLabelNode)
        );
    }

    @Override
    InsnList generateInvokeReplacementInstructions() {
        FlowInstrumentationVariables vars = getFlowInstrumentationVariables();
        MonitorInstrumentationInstructions monInsts = getMonitorInstrumentationInstructions();
        
        Variable contArg = vars.getContArg();
        Variable pendingCountVar = vars.getPendingCountVar();
        Variable savedLocalsVar = vars.getSavedLocalsVar();
        Variable savedStackVar = vars.getSavedStackVar();
        Variable tempObjVar = vars.getTempObjectVar();
        
        InsnList loadLockStateToStackInsnList = monInsts.getLoadLockStateToStackInsnList();
        InsnList exitMonitorsInLockStateInsnList = monInsts.getExitMonitorsInLockStateInsnList();

        Type returnType = getReturnType();
        
        Frame<BasicValue> frame = getFrame();
        
        //          restorePoint_<number>_normalExecute: // at this label: normal stack / normal var table
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
        //
        //          restorePoint_<number>_loadExecute: // at this label: empty stack / empty var table
        //          enterLocks(lockState);
        //          continuation.addPending(methodState); // method state should be loaded from Continuation.saved
        //          restoreStackSuffix(stack, <number of items required for method invocation below>);
        //          <method invocation>
        //          if (continuation.getMode() == MODE_SAVING) {
        //              exitLocks(lockState);
        //              return <dummy>;
        //          }
        //          restoreOperandStack(stack);
        //          restoreLocalsStack(localVars);
        //          restorePoint_<number>_end;
        //          goto restorePoint_<number>_end;
        return merge(
                // debugPrint("clear excess pending" + methodNode.name),
                call(CONTINUATION_CLEAREXCESSPENDING_METHOD, loadVar(contArg), loadVar(pendingCountVar)),
                // debugPrint("saving operand stack" + methodNode.name),
                saveOperandStack(savedStackVar, tempObjVar, frame),
                // debugPrint("saving locals" + methodNode.name),
                saveLocalVariableTable(savedLocalsVar, tempObjVar, frame),
                // debugPrint("calling addPending" + methodNode.name),
                call(CONTINUATION_ADDPENDING_METHOD, loadVar(contArg),
                        construct(METHODSTATE_INIT_METHOD,
                                loadIntConst(getId()),
                                loadVar(savedStackVar),
                                loadVar(savedLocalsVar),
                                cloneInsnList(loadLockStateToStackInsnList) // inserted many times, must be cloned
                        )
                ),
                // debugPrint("invoking" + methodNode.name),
                cloneInvokeNode(getInvokeInsnNode()), // invoke method
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
                // debugPrint("finished" + methodNode.name),

                
                
                
                addLabel(continueExecLabelNode)
        );
    }
    
}
