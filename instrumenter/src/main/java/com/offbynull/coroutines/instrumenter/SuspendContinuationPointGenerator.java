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
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.construct;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.empty;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.jumpTo;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.lineNumber;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadIntConst;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadLocalVariableTable;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadOperandStack;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadVar;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.merge;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.pop;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.returnDummy;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.saveLocalVariableTable;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.saveOperandStack;
import com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable;
import static com.offbynull.coroutines.user.Continuation.MODE_NORMAL;
import static com.offbynull.coroutines.user.Continuation.MODE_SAVING;
import java.util.Collections;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

final class SuspendContinuationPointGenerator extends ContinuationPointGenerator {

    public SuspendContinuationPointGenerator(
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
        Variable savedLocalsVar = vars.getSavedLocalsVar();
        Variable savedStackVar = vars.getSavedStackVar();
        Variable tempObjVar = vars.getTempObjectVar();
        
        InsnList enterMonitorsInLockStateInsnList = monInsts.getEnterMonitorsInLockStateInsnList();

        Integer lineNum = getLineNumber();
        
        Frame<BasicValue> frame = getFrame();
        
        //          enterLocks(lockState);
        //          restoreOperandStack(stack);
        //          restoreLocalsStack(localVars);
        //          continuation.setMode(MODE_NORMAL);
        //          goto restorePoint_<number>_continue;
        return merge(
                lineNum == null ? empty() : lineNumber(lineNum),
                loadOperandStack(savedStackVar, tempObjVar, frame),
                loadLocalVariableTable(savedLocalsVar, tempObjVar, frame),
                cloneInsnList(enterMonitorsInLockStateInsnList),
                pop(), // frame at the time of invocation to Continuation.suspend() has Continuation reference on the
                       // stack that would have been consumed by that invocation... since we're removing that call, we
                       // also need to pop the Continuation reference from the stack... it's important that we
                       // explicitly do it at this point becuase during loading the stack will be restored with top
                       // of stack pointing to that continuation object
                call(CONTINUATION_SETMODE_METHOD, loadVar(contArg), loadIntConst(MODE_NORMAL)),
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
        //
        //          restorePoint_<number>_continue: // at this label: empty exec stack / uninit exec var table
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
                call(CONTINUATION_SETMODE_METHOD, loadVar(contArg), loadIntConst(MODE_SAVING)),
                cloneInsnList(exitMonitorsInLockStateInsnList), // used several times, must be cloned
                returnDummy(returnType), // return dummy value
                
                
                
                addLabel(continueExecLabelNode)
        );
    }
    
}
