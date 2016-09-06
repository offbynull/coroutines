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

import static com.offbynull.coroutines.instrumenter.ContinuationInstructionGenerationUtils.loadLocalVariableTable;
import static com.offbynull.coroutines.instrumenter.ContinuationInstructionGenerationUtils.loadOperandStackPrefix;
import static com.offbynull.coroutines.instrumenter.ContinuationInstructionGenerationUtils.loadOperandStackSuffix;
import static com.offbynull.coroutines.instrumenter.ContinuationInstructionGenerationUtils.popMethodResult;
import static com.offbynull.coroutines.instrumenter.ContinuationInstructionGenerationUtils.returnDummy;
import static com.offbynull.coroutines.instrumenter.ContinuationInstructionGenerationUtils.saveLocalVariableTable;
import static com.offbynull.coroutines.instrumenter.ContinuationInstructionGenerationUtils.saveOperandStack;
import static com.offbynull.coroutines.instrumenter.ContinuationPointInstructionUtils.castToObjectAndSave;
import static com.offbynull.coroutines.instrumenter.ContinuationPointInstructionUtils.loadAndCastToOriginal;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.addLabel;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.call;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.cloneInvokeNode;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.construct;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.empty;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.ifIntegersEqual;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.jumpTo;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.lineNumber;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.loadIntConst;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.loadVar;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.merge;
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
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.combineObjectArrays;
import static com.offbynull.coroutines.instrumenter.asm.MethodInvokeUtils.getArgumentCountRequiredForInvocation;
import static com.offbynull.coroutines.instrumenter.asm.MethodInvokeUtils.getReturnTypeOfInvocation;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.cloneInsnList;

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
        Variable tempObjVar2 = vars.getTempObjVar2();
        
        InsnList enterMonitorsInLockStateInsnList = monInsts.getEnterMonitorsInLockStateInsnList();
        InsnList exitMonitorsInLockStateInsnList = monInsts.getExitMonitorsInLockStateInsnList();
        
        Type invokeMethodReturnType = getReturnTypeOfInvocation(getInvokeInsnNode());        
        Type returnType = getReturnType();
        Integer lineNum = getLineNumber();
        int methodStackCount = getArgumentCountRequiredForInvocation(getInvokeInsnNode());
        
        Frame<BasicValue> frame = getFrame();
        
        //          enterLocks(lockState);
        //              // Load up enough of the stack to invoke the method. The invocation here needs to be wrapped in a try catch because
        //              // the original invocation was within a try catch block (at least 1, maybe more). If we do get a throwable, jump
        //              // back to the area where the original invocation was and rethrow it there so the proper catch handlers can
        //              // handle it (if the handler is for the expected throwable type).
        //          restoreStackSuffix(stack, <number of items required for method invocation below>);
        //          <method invocation>
        //          if (continuation.getMode() == MODE_SAVING) {
        //              exitLocks(lockState);
        //              continuation.addPending(methodState); // method state should be loaded from Continuation.saved
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
                // debugPrint("entering monitors"),
                cloneInsnList(enterMonitorsInLockStateInsnList),
                // debugPrint("loading just enough in to the stack to invoke method"),
                // debugPrint(call(MethodUtils.getAccessibleMethod(Arrays.class, "toString", Object[].class), loadVar(savedStackVar))),
                // debugPrint("method stack count " + methodStackCount),
                loadOperandStackSuffix(savedStackVar, frame, methodStackCount),
                // debugPrint("invoking method"),
                cloneInvokeNode(getInvokeInsnNode()), // invoke method  
                ifIntegersEqual(// if we're saving after invoke, return dummy value
                        call(CONTINUATION_GETMODE_METHOD, loadVar(contArg)),
                        loadIntConst(MODE_SAVING),
                        merge(
                                // debugPrint("no continuation, save signal recvd"),
                                // debugPrint("popping dummy result of method off stack (if it exists)"),
                                popMethodResult(getInvokeInsnNode()),
                                // debugPrint("exiting monitors"),
                                cloneInsnList(exitMonitorsInLockStateInsnList), // inserted many times, must be cloned
                                // debugPrint("adding pending method"),
                                call(CONTINUATION_ADDPENDING_METHOD, loadVar(contArg), loadVar(methodStateVar)),
                                // debugPrint("returning"),
                                // debugPrint("---------------------------"),
                                returnDummy(returnType)
                        )
                ),
                // debugPrint("saving result"),
                castToObjectAndSave(invokeMethodReturnType, tempObjVar2), // save return (does nothing if void)
                // debugPrint("loading remainder of stack"),
                loadOperandStackPrefix(savedStackVar, frame, frame.getStackSize() - methodStackCount),
                // debugPrint("loading local vars"),
                loadLocalVariableTable(savedLocalsVar, frame),
                // debugPrint("loading result"),
                loadAndCastToOriginal(invokeMethodReturnType, tempObjVar2),
                // debugPrint("continuing..."),
                jumpTo(continueExecLabelNode)
        );
    }

    private InsnList generateInvokeReplacementInstructions(LabelNode continueExecLabelNode) {
        FlowInstrumentationVariables vars = getFlowInstrumentationVariables();
        MonitorInstrumentationInstructions monInsts = getMonitorInstrumentationInstructions();
        
        Variable contArg = vars.getContArg();
        Variable savedLocalsVar = vars.getSavedLocalsVar();
        Variable savedStackVar = vars.getSavedStackVar();
        Variable savedPartialStackVar = vars.getSavedPartialStackVar();
        Variable savedArgsVar = vars.getSavedArgumentsVar();
        
        InsnList loadLockStateToStackInsnList = monInsts.getLoadLockStateToStackInsnList();
        InsnList exitMonitorsInLockStateInsnList = monInsts.getExitMonitorsInLockStateInsnList();

        Type returnType = getReturnType();
        
        Frame<BasicValue> frame = getFrame();
        
        //          Object[] duplicatedArgs = saveOperandStack(<method param count>); -- Why do we do this? because when we want to save the
        //                                                                            -- args to this method when we call
        //                                                                            -- saveOperandStack(). We need to save here becuase
        //                                                                            -- once we invoke the method the args will be consumed
        //                                                                            -- off the stack. The args need to be saved because
        //                                                                            -- when we load, we need to call in to this method
        //                                                                            -- again (see loading code generator above).
        //          <method invocation>
        //          if (continuation.getMode() == MODE_SAVING) {
        //              Object[] stack = saveOperandStack();
        //              Object[] locals = saveLocals();
        //              exitLocks(lockState);
        //              continuation.addPending(new MethodState(<number>, stack, locals, lockState);
        //              return <dummy>;
        //          }
        //
        //
        //          restorePoint_<number>_continue:
        
        int stackCountForMethodInvocation = getArgumentCountRequiredForInvocation(getInvokeInsnNode());
        int preInvokeStackSize = frame.getStackSize();
        int postInvokeStackSize = frame.getStackSize() - stackCountForMethodInvocation;
        return merge(
                //debugPrint("saving method args from operand stack"),
                // save args for invoke
                saveOperandStack(savedArgsVar, frame, preInvokeStackSize, stackCountForMethodInvocation),
                //debugPrint("invoking method -- method args should be off the stack at this point"),
                cloneInvokeNode(getInvokeInsnNode()),
                ifIntegersEqual(// if we're saving after invoke
                        call(CONTINUATION_GETMODE_METHOD, loadVar(contArg)),
                        loadIntConst(MODE_SAVING),
                        merge(
                                //debugPrint("no continuation, save signal recvd"),
                                //debugPrint("popping dummy result of method off stack (if it exists)"),
                                popMethodResult(getInvokeInsnNode()),
                                //debugPrint("saving remainder of operand stack"),
                                // since we invoked the method before getting here, we already consumed the arguments that were sitting
                                // on the stack waiting to be consumed by the method -- as such, subtract the number of arguments from the
                                // total stack size when saving!!!!! The top of the stack should now be just before the arguments!!!
                                //   THIS IS SUPER IMPORTANT!!!!!
                                saveOperandStack(savedPartialStackVar, frame, postInvokeStackSize, postInvokeStackSize),
                                //debugPrint("combining saved args with remainder of operand stack to get full stack required for loading"),
                                combineObjectArrays(savedStackVar, savedPartialStackVar, savedArgsVar),
                                //debugPrint("saving local vars table"),
                                saveLocalVariableTable(savedLocalsVar, frame),
                                //debugPrint("exiting monitors"),
                                cloneInsnList(exitMonitorsInLockStateInsnList), // inserted many times, must be cloned
                                //debugPrint("adding pending method state"),
                                call(CONTINUATION_ADDPENDING_METHOD, loadVar(contArg),
                                        construct(METHODSTATE_INIT_METHOD,
                                                loadIntConst(getId()),
                                                loadVar(savedStackVar),
                                                loadVar(savedLocalsVar),
                                                cloneInsnList(loadLockStateToStackInsnList) // inserted many times, must be cloned
                                        )
                                ),
                                //debugPrint("returning dummy value"),
                                //debugPrint("--------------------------------------"),
                                returnDummy(returnType)
                        )
                ),      

                
                
                
                addLabel(continueExecLabelNode)
        );
    }
    
}
