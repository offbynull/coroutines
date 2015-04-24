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
import java.util.Collections;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

final class InvokeContinuationPointGenerator extends ContinuationPointGenerator {

    public InvokeContinuationPointGenerator(
            int id,
            AbstractInsnNode invokeInsnNode,
            LineNumberNode invokeLineNumberNode,
            Frame<BasicValue> frame,
            Type returnType,
            FlowInstrumentationVariables flowInstrumentationVariables,
            MonitorInstrumentationInstructions monitorInstrumentationInstructions) {
        super(id, invokeInsnNode, invokeLineNumberNode, frame, returnType, flowInstrumentationVariables,
                monitorInstrumentationInstructions);
    }
    
    @Override
    ContinuationPointInstructions generate() {
        LabelNode continueExecLabelNode = new LabelNode();
        return new ContinuationPointInstructions(
                getInvokeInsnNode(),
                generateLoadInstructions(continueExecLabelNode),
                generateInvokeReplacementInstructions(continueExecLabelNode),
                Collections.emptyList());
    }
    
    private InsnList generateLoadInstructions(LabelNode continueExecLabelNode) {
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
        
        //          enterLocks(lockState);
        //          continuation.addPending(methodState); // method state should be loaded from Continuation.saved
        //              // Load up enough of the stack to invoke the method. The invocation here needs to be wrapped in a try catch because
        //              // the original invocation was within a try catch block (at least 1, maybe more). If we do get a throwable, jump
        //              // back to the area where the original invocation was and rethrow it there so the proper catch handlers can
        //              // handle it (if the handler is for the expected throwable type).
        //          restoreStackSuffix(stack, <number of items required for method invocation below>);
        //          <method invocation>
        //          if (continuation.getMode() == MODE_SAVING) {
        //              exitLocks(lockState);
        //              return <dummy>;
        //          }
        //             // At this point the invocation happened successfully, so we want to save the invocation's result, restore this
        //             // method's state, and then put the result on top of the stack as if invocation just happened. We then jump in to
        //             // the method and continue running it from the instruction after the original invocation point.
        //          tempObjVar2 = <method invocation>'s return value; // does nothing if ret type is void
        //          restoreOperandStack(stack);
        //          restoreLocalsStack(localVars);
        //          place tempObjVar2 on top of stack if not void (as if it <method invocation> were just run and returned that value)
        //          goto restorePoint_<number>_continue;
        return merge(lineNum == null ? empty() : lineNumber(lineNum),
                cloneInsnList(enterMonitorsInLockStateInsnList),
                call(CONTINUATION_ADDPENDING_METHOD, loadVar(contArg), loadVar(methodStateVar)),
                loadOperandStackSuffix(savedStackVar, tempObjVar, frame, methodStackCount),
                cloneInvokeNode(getInvokeInsnNode()), // invoke method  
                ifIntegersEqual(// if we're saving after invoke, return dummy value
                        call(CONTINUATION_GETMODE_METHOD, loadVar(contArg)),
                        loadIntConst(MODE_SAVING),
                        merge(
                                cloneInsnList(exitMonitorsInLockStateInsnList), // inserted many times, must be cloned
                                returnDummy(returnType)
                        )
                ),
                castToObjectAndSave(invokeMethodReturnType, tempObjVar2), // save return (does nothing if void)
                loadOperandStackPrefix(savedStackVar, tempObjVar, frame, frame.getStackSize() - methodStackCount),
                loadLocalVariableTable(savedLocalsVar, tempObjVar, frame),
                loadAndCastToOriginal(invokeMethodReturnType, tempObjVar2),
                jumpTo(continueExecLabelNode)
        );
    }

    private InsnList generateInvokeReplacementInstructions(LabelNode continueExecLabelNode) {
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
        
        //             // Clear any excess pending MethodStates that may be lingering. We need to do this because we may have one or more
        //             // excess pending method states sitting around if invocation continuation points (methods that take in a continuation
        //             // object) were called previously in this method. In the event that such a previous call threw an exception, there
        //             // may be more than 1 excess item -- clearExcessPending() makes sure to clear all excess items.
        //             //
        //             // The pendingCount variable contains the excpected number of pending method states
        //          continuation.clearExcessPending(pendingCount);
        //          Object[] stack = saveOperandStack();
        //          Object[] locals = saveLocals();
        //          continuation.addPending(new MethodState(<number>, stack, locals, lockState);
        //          <method invocation>
        //          if (continuation.getMode() == MODE_SAVING) {
        //              exitLocks(lockState);
        //              return <dummy>;
        //          }
        //
        //
        //          restorePoint_<number>_continue:
        
        return merge(
                call(CONTINUATION_CLEAREXCESSPENDING_METHOD, loadVar(contArg), loadVar(pendingCountVar)),
                saveOperandStack(savedStackVar, tempObjVar, frame),
                saveLocalVariableTable(savedLocalsVar, tempObjVar, frame),
                call(CONTINUATION_ADDPENDING_METHOD, loadVar(contArg),
                        construct(METHODSTATE_INIT_METHOD,
                                loadIntConst(getId()),
                                loadVar(savedStackVar),
                                loadVar(savedLocalsVar),
                                cloneInsnList(loadLockStateToStackInsnList) // inserted many times, must be cloned
                        )
                ),
                cloneInvokeNode(getInvokeInsnNode()), // invoke method
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

                
                
                
                addLabel(continueExecLabelNode)
        );
    }
    
}
