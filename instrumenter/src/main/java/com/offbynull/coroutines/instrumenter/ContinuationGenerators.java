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

import static com.offbynull.coroutines.instrumenter.StateGenerators.loadLocalVariableTable;
import static com.offbynull.coroutines.instrumenter.StateGenerators.loadOperandStackPrefix;
import static com.offbynull.coroutines.instrumenter.StateGenerators.loadOperandStackSuffix;
import static com.offbynull.coroutines.instrumenter.StateGenerators.saveLocalVariableTable;
import static com.offbynull.coroutines.instrumenter.SynchronizationGenerators.createMonitorContainerAndSaveToVar;
import static com.offbynull.coroutines.instrumenter.SynchronizationGenerators.enterStoredMonitors;
import static com.offbynull.coroutines.instrumenter.SynchronizationGenerators.exitStoredMonitors;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.addLabel;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.call;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.cloneInvokeNode;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.combineObjectArrays;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.construct;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.ifIntegersEqual;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.jumpTo;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.loadIntConst;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.loadNull;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.loadVar;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.merge;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.mergeIf;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.saveVar;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.tableSwitch;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.throwThrowable;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.tryCatchBlock;
import static com.offbynull.coroutines.instrumenter.asm.MethodInvokeUtils.getArgumentCountRequiredForInvocation;
import static com.offbynull.coroutines.instrumenter.asm.MethodInvokeUtils.getReturnTypeOfInvocation;
import com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable;
import com.offbynull.coroutines.user.Continuation;
import static com.offbynull.coroutines.user.Continuation.MODE_NORMAL;
import static com.offbynull.coroutines.user.Continuation.MODE_SAVING;
import com.offbynull.coroutines.user.LockState;
import com.offbynull.coroutines.user.MethodState;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.throwRuntimeException;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.pop;
import static com.offbynull.coroutines.instrumenter.StateGenerators.loadOperandStack;
import static com.offbynull.coroutines.instrumenter.StateGenerators.saveOperandStack;

final class ContinuationGenerators {
    
    private static final Method CONTINUATION_GETMODE_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "getMode");
    private static final Method CONTINUATION_SETMODE_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "setMode", Integer.TYPE);
    private static final Method CONTINUATION_REMOVEFIRSTSAVED_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "removeFirstSaved");
    private static final Method CONTINUATION_ADDPENDING_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "addPending", MethodState.class);
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
    
    private ContinuationGenerators() {
        // do nothing
    }
    
    public static InsnList entryPointLoader(MethodProperties props) {
        Validate.notNull(props);

        Variable contArg = props.getCoreVariables().getContinuationArgVar();
        Variable methodStateVar = props.getCoreVariables().getMethodStateVar();
        Variable savedLocalsVar = props.getCoreVariables().getSavedLocalsVar();
        Variable savedStackVar = props.getCoreVariables().getSavedStackVar();
        
        Variable lockStateVar = props.getLockVariables().getLockStateVar();

        String methodName = props.getMethodName();
        int numOfContinuationPoints = props.getContinuationPoints().size();

        LabelNode startOfMethodLabelNode = new LabelNode();
        return merge(tableSwitch(call(CONTINUATION_GETMODE_METHOD, loadVar(contArg)),
                        throwRuntimeException("Unrecognized state"),
                        0,
                        // CASE 0 (FRESH)...
                        merge(
                                // fresh invoke
                                createMonitorContainerAndSaveToVar(props),
                                jumpTo(startOfMethodLabelNode)
                        ),
                        // CASE 1 (SAVING)...
                        throwRuntimeException("Unexpected state (saving not allowed at this point)"),
                        // CASE 2 (LOADING)...
                        merge(// restore invoke
                                call(CONTINUATION_REMOVEFIRSTSAVED_METHOD, loadVar(contArg)),
                                saveVar(methodStateVar),
                                call(METHODSTATE_GETLOCALTABLE_METHOD, loadVar(methodStateVar)),
                                saveVar(savedLocalsVar),
                                call(METHODSTATE_GETSTACK_METHOD, loadVar(methodStateVar)),
                                saveVar(savedStackVar),
                                // get lockstate if method actually has monitorenter/exit insn in it (var != null if this were the case)
                                mergeIf(lockStateVar != null, () -> new Object[] {
                                        call(METHODSTATE_GETLOCKSTATE_METHOD, loadVar(methodStateVar)),
                                        saveVar(lockStateVar)
                                }).generate(),
                                tableSwitch(call(METHODSTATE_GETCONTINUATIONPOINT_METHOD, loadVar(methodStateVar)),
                                        throwRuntimeException("Unrecognized restore id " + methodName),
                                        0,
                                        IntStream.range(0, numOfContinuationPoints)
                                                .mapToObj(idx -> restoreState(props, idx))
                                                .toArray((x) -> new InsnList[x])
                                )
                                // jump to not required here, switch above either throws exception or jumps to restore point
                        )
                ),
                addLabel(startOfMethodLabelNode)
        );
    }




    public static InsnList restoreState(MethodProperties props, int idx) {
        Validate.notNull(props);
        Validate.isTrue(idx >= 0);
        ContinuationPoint continuationPoint = validateAndGetContinuationPoint(props, idx, ContinuationPoint.class);
        
        if (continuationPoint instanceof SuspendContinuationPoint) {
            return restoreStateFromSuspend(props, idx);
        } else if (continuationPoint instanceof NormalInvokeContinuationPoint) {
            return restoreStateFromNormalInvocation(props, idx);
        } else if (continuationPoint instanceof TryCatchInvokeContinuationPoint) {
            return restoreStateFromInvocationWithinTryCatch(props, idx);
        } else {
            throw new IllegalArgumentException(); // should never happen
        }
    }
    
    private static InsnList restoreStateFromSuspend(MethodProperties props, int idx) {
        Validate.notNull(props);
        Validate.isTrue(idx >= 0);
        SuspendContinuationPoint cp = validateAndGetContinuationPoint(props, idx, SuspendContinuationPoint.class);

        Variable contArg = props.getCoreVariables().getContinuationArgVar();
        Variable savedLocalsVar = props.getCoreVariables().getSavedLocalsVar();
        Variable savedStackVar = props.getCoreVariables().getSavedStackVar();
        
        Frame<BasicValue> frame = cp.getFrame();
        LabelNode continueExecLabelNode = cp.getContinueExecutionLabel();
        
        //          enterLocks(lockState);
        //          restoreOperandStack(stack);
        //          restoreLocalsStack(localVars);
        //          continuation.setMode(MODE_NORMAL);
        //          goto restorePoint_<number>_continue;
        return merge(
                loadOperandStack(savedStackVar, frame),
                loadLocalVariableTable(savedLocalsVar, frame),
                enterStoredMonitors(props),
                pop(), // frame at the time of invocation to Continuation.suspend() has Continuation reference on the
                       // stack that would have been consumed by that invocation... since we're removing that call, we
                       // also need to pop the Continuation reference from the stack... it's important that we
                       // explicitly do it at this point becuase during loading the stack will be restored with top
                       // of stack pointing to that continuation object
                call(CONTINUATION_SETMODE_METHOD, loadVar(contArg), loadIntConst(MODE_NORMAL)),
                jumpTo(continueExecLabelNode)
        );
    }
    
    private static InsnList restoreStateFromNormalInvocation(MethodProperties props, int idx) {
        Validate.notNull(props);
        Validate.isTrue(idx >= 0);
        NormalInvokeContinuationPoint cp = validateAndGetContinuationPoint(props, idx, NormalInvokeContinuationPoint.class);
        
        Variable contArg = props.getCoreVariables().getContinuationArgVar();
        Variable methodStateVar = props.getCoreVariables().getMethodStateVar();
        Variable savedLocalsVar = props.getCoreVariables().getSavedLocalsVar();
        Variable savedStackVar = props.getCoreVariables().getSavedStackVar();
        
        Type returnType = props.getReturnType();
        
        Frame<BasicValue> frame = cp.getFrame();
        MethodInsnNode invokeNode = cp.getInvokeInstruction();
        LabelNode continueExecLabelNode = cp.getContinueExecutionLabel();
        
        Type invokeReturnType = getReturnTypeOfInvocation(invokeNode);        
        int invokeArgCount = getArgumentCountRequiredForInvocation(invokeNode);
        
        Variable returnCacheVar = getReturnCacheVar(props, invokeReturnType); // will be null if void
        
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
        return merge(
                // debugPrint("entering monitors"),
                enterStoredMonitors(props),
                // debugPrint("loading just enough in to the stack to invoke method"),
                // debugPrint(call(MethodUtils.getAccessibleMethod(Arrays.class, "toString", Object[].class), loadVar(savedStackVar))),
                // debugPrint("method stack count " + methodStackCount),
                loadOperandStackSuffix(savedStackVar, frame, invokeArgCount),
                // debugPrint("invoking method"),
                cloneInvokeNode(invokeNode), // invoke method  (ADDED MULTIPLE TIMES -- MUST BE CLONED)
                ifIntegersEqual(// if we're saving after invoke, return dummy value
                        call(CONTINUATION_GETMODE_METHOD, loadVar(contArg)),
                        loadIntConst(MODE_SAVING),
                        merge(
                                // debugPrint("no continuation, save signal recvd"),
                                // debugPrint("popping dummy result of method off stack (if it exists)"),
                                popMethodResult(invokeNode),
                                // debugPrint("exiting monitors"),
                                exitStoredMonitors(props),
                                // debugPrint("adding pending method"),
                                call(CONTINUATION_ADDPENDING_METHOD, loadVar(contArg), loadVar(methodStateVar)),
                                // debugPrint("returning"),
                                // debugPrint("---------------------------"),
                                returnDummy(returnType)
                        )
                ),
                // save return (if returnCacheVar is null means ret type is void)
                mergeIf(returnCacheVar != null, () -> new Object[] {
                    saveVar(returnCacheVar)
                }),
                // load remaninder of operandstack and localvars
                loadOperandStackPrefix(savedStackVar, frame, frame.getStackSize() - invokeArgCount),
                loadLocalVariableTable(savedLocalsVar, frame),
                // load return (if returnCacheVar is null means ret type is void)
                mergeIf(returnCacheVar != null, () -> new Object[] {
                    loadVar(returnCacheVar)
                }),
                jumpTo(continueExecLabelNode) // continuing...
        );
    }
    
    private static InsnList restoreStateFromInvocationWithinTryCatch(MethodProperties props, int idx) {
        Validate.notNull(props);
        Validate.isTrue(idx >= 0);
        TryCatchInvokeContinuationPoint cp = validateAndGetContinuationPoint(props, idx, TryCatchInvokeContinuationPoint.class);
        
        Variable contArg = props.getCoreVariables().getContinuationArgVar();
        Variable methodStateVar = props.getCoreVariables().getMethodStateVar();
        Variable savedLocalsVar = props.getCoreVariables().getSavedLocalsVar();
        Variable savedStackVar = props.getCoreVariables().getSavedStackVar();

        Variable throwableVar = props.getCacheVariables().getThrowableCacheVar();
        
        Type returnType = props.getReturnType();
        
        // tryCatchBlock() invocation further on in this method will populate TryCatchBlockNode fields
        TryCatchBlockNode newTryCatchBlockNode = cp.getTryCatchBlock();

        Frame<BasicValue> frame = cp.getFrame();
        MethodInsnNode invokeNode = cp.getInvokeInstruction();
        LabelNode continueExecLabelNode = cp.getContinueExecutionLabel();
        LabelNode exceptionExecutionLabelNode = cp.getExceptionExecutionLabel();
        
        Type invokeReturnType = getReturnTypeOfInvocation(invokeNode);        
        int invokeArgCount = getArgumentCountRequiredForInvocation(invokeNode);
        
        Variable returnCacheVar = getReturnCacheVar(props, invokeReturnType); // will be null if void
        
        //          enterLocks(lockState);
        //          continuation.addPending(methodState); // method state should be loaded from Continuation.saved
        //              // Load up enough of the stack to invoke the method. The invocation here needs to be wrapped in a try catch because
        //              // the original invocation was within a try catch block (at least 1, maybe more). If we do get a throwable, jump
        //              // back to the area where the original invocation was and rethrow it there so the proper catch handlers can
        //              // handle it (if the handler is for the expected throwable type).
        //          restoreStackSuffix(stack, <number of items required for method invocation below>);
        //          try {
        //              <method invocation>
        //          } catch (throwable) {
        //              tempObjVar2 = throwable;
        //              restoreOperandStack(stack);
        //              restoreLocalsStack(localVars);
        //              goto restorePoint_<number>_rethrow;
        //          }
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
        
        return merge(
                enterStoredMonitors(props),
                loadOperandStackSuffix(savedStackVar, frame, invokeArgCount),
                tryCatchBlock(
                        newTryCatchBlockNode,
                        null,
                        merge(// try
                                cloneInvokeNode(invokeNode) // invoke method  (ADDED MULTIPLE TIMES -- MUST BE CLONED)
                        ),
                        merge(// catch(any)
                                saveVar(throwableVar),
                                loadOperandStackPrefix(savedStackVar, frame, frame.getStackSize() - invokeArgCount),
                                loadLocalVariableTable(savedLocalsVar, frame),
                                jumpTo(exceptionExecutionLabelNode)
                        )
                ),
                ifIntegersEqual(// if we're saving after invoke, return dummy value
                        call(CONTINUATION_GETMODE_METHOD, loadVar(contArg)),
                        loadIntConst(MODE_SAVING),
                        merge(
                                popMethodResult(invokeNode),
                                exitStoredMonitors(props), // inserted many times, must be cloned
                                call(CONTINUATION_ADDPENDING_METHOD, loadVar(contArg), loadVar(methodStateVar)),
                                returnDummy(returnType)
                        )
                ),
                mergeIf(returnCacheVar != null, () -> new Object[] {// save return (if returnCacheVar is null means ret type is void)
                    saveVar(returnCacheVar)
                }),
                loadOperandStackPrefix(savedStackVar, frame, frame.getStackSize() - invokeArgCount),
                loadLocalVariableTable(savedLocalsVar, frame),
                mergeIf(returnCacheVar != null, () -> new Object[] {// load return (if returnCacheVar is null means ret type is void)
                    loadVar(returnCacheVar)
                }),
                jumpTo(continueExecLabelNode)
        );
    }

    
    
    
    
    
    
    private static Variable getReturnCacheVar(MethodProperties props, Type type) {
        Validate.notNull(props);
        Validate.notNull(type);

        switch (type.getSort()) {
            case Type.BOOLEAN:
                return props.getCacheVariables().getBooleanReturnCacheVar();
            case Type.BYTE:
                return props.getCacheVariables().getByteReturnCacheVar();
            case Type.CHAR:
                return props.getCacheVariables().getCharReturnCacheVar();
            case Type.SHORT:
                return props.getCacheVariables().getShortReturnCacheVar();
            case Type.INT:
                return props.getCacheVariables().getIntReturnCacheVar();
            case Type.LONG:
                return props.getCacheVariables().getLongReturnCacheVar();
            case Type.FLOAT:
                return props.getCacheVariables().getFloatReturnCacheVar();
            case Type.DOUBLE:
                return props.getCacheVariables().getDoubleReturnCacheVar();
            case Type.ARRAY:
            case Type.OBJECT:
                return props.getCacheVariables().getObjectReturnCacheVar();
            case Type.VOID:
                return null;
            default:
                throw new IllegalArgumentException("Bad type");
        }
    }
    
    
    
    
    public static InsnList saveState(MethodProperties props, int idx) {
        Validate.notNull(props);
        Validate.isTrue(idx >= 0);
        ContinuationPoint continuationPoint = validateAndGetContinuationPoint(props, idx, ContinuationPoint.class);
        
        if (continuationPoint instanceof SuspendContinuationPoint) {
            return saveStateFromSuspend(props, idx);
        } else if (continuationPoint instanceof NormalInvokeContinuationPoint) {
            return saveStateFromNormalInvocation(props, idx);
        } else if (continuationPoint instanceof TryCatchInvokeContinuationPoint) {
            return saveStateFromInvocationWithinTryCatch(props, idx);
        } else {
            throw new IllegalArgumentException(); // should never happen
        }
    }
    
    private static InsnList saveStateFromSuspend(MethodProperties props, int idx) {
        Validate.notNull(props);
        Validate.isTrue(idx >= 0);
        SuspendContinuationPoint cp = validateAndGetContinuationPoint(props, idx, SuspendContinuationPoint.class);

        Variable contArg = props.getCoreVariables().getContinuationArgVar();
        Variable savedLocalsVar = props.getCoreVariables().getSavedLocalsVar();
        Variable savedStackVar = props.getCoreVariables().getSavedStackVar();
        
        Variable lockStateVar = props.getLockVariables().getLockStateVar();
        
        Type returnType = props.getReturnType();
        
        Frame<BasicValue> frame = cp.getFrame();
        LabelNode continueExecLabelNode = cp.getContinueExecutionLabel();
        
        //          Object[] stack = saveOperandStack();
        //          Object[] locals = saveLocals();
        //          continuation.addPending(new MethodState(<number>, stack, locals, lockState);
        //          continuation.setMode(MODE_SAVING);
        //          exitLocks(lockState);
        //          return <dummy>;
        //
        //
        //          restorePoint_<number>_continue: // at this label: empty exec stack / uninit exec var table
        return merge(saveOperandStack(savedStackVar, frame),
                saveLocalVariableTable(savedLocalsVar, frame),
                call(CONTINUATION_ADDPENDING_METHOD, loadVar(contArg),
                        construct(METHODSTATE_INIT_METHOD,
                                loadIntConst(idx),
                                loadVar(savedStackVar),
                                loadVar(savedLocalsVar),
                                // load lockstate for last arg if method actually has monitorenter/exit insn in it
                                // (var != null if this were the case), otherwise load null for that arg
                                mergeIf(lockStateVar != null, () -> new Object[] {
                                        loadVar(lockStateVar)
                                }).mergeIf(lockStateVar == null, () -> new Object[] {
                                        loadNull()
                                }).generate()
                        )
                ),
                call(CONTINUATION_SETMODE_METHOD, loadVar(contArg), loadIntConst(MODE_SAVING)),
                exitStoredMonitors(props), // used several times, must be cloned
                returnDummy(returnType), // return dummy value
                
                
                
                addLabel(continueExecLabelNode)
        );
    }
    
    private static InsnList saveStateFromNormalInvocation(MethodProperties props, int idx) {
        Validate.notNull(props);
        Validate.isTrue(idx >= 0);
        NormalInvokeContinuationPoint cp = validateAndGetContinuationPoint(props, idx, NormalInvokeContinuationPoint.class);

        Variable contArg = props.getCoreVariables().getContinuationArgVar();
        Variable savedLocalsVar = props.getCoreVariables().getSavedLocalsVar();
        Variable savedStackVar = props.getCoreVariables().getSavedStackVar();

        Variable savedPartialStackVar = props.getCoreVariables().getSavedPartialStackVar();
        Variable savedArgsVar = props.getCoreVariables().getSavedArgumentsVar();
        
        Variable lockStateVar = props.getLockVariables().getLockStateVar();

        Type returnType = props.getReturnType();
        
        Frame<BasicValue> frame = cp.getFrame();
        MethodInsnNode invokeNode = cp.getInvokeInstruction();
        LabelNode continueExecLabelNode = cp.getContinueExecutionLabel();
        
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
        
        int invokeArgCount = getArgumentCountRequiredForInvocation(invokeNode);
        int preInvokeStackSize = frame.getStackSize();
        int postInvokeStackSize = frame.getStackSize() - invokeArgCount;
        return merge(//debugPrint("saving method args from operand stack"),
                // save args for invoke
                saveOperandStack(savedArgsVar, frame, preInvokeStackSize, invokeArgCount),
                //debugPrint("invoking method -- method args should be off the stack at this point"),
                cloneInvokeNode(invokeNode), // invoke method  (ADDED MULTIPLE TIMES -- MUST BE CLONED)
                        ifIntegersEqual(// if we're saving after invoke
                        call(CONTINUATION_GETMODE_METHOD, loadVar(contArg)),
                        loadIntConst(MODE_SAVING),
                        merge(//debugPrint("no continuation, save signal recvd"),
                                //debugPrint("popping dummy result of method off stack (if it exists)"),
                                popMethodResult(invokeNode),
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
                                exitStoredMonitors(props), // inserted many times, must be cloned
                                //debugPrint("adding pending method state"),
                                call(CONTINUATION_ADDPENDING_METHOD, loadVar(contArg),
                                        construct(METHODSTATE_INIT_METHOD,
                                                loadIntConst(idx),
                                                loadVar(savedStackVar),
                                                loadVar(savedLocalsVar),
                                                // load lockstate for last arg if method actually has monitorenter/exit insn in it
                                                // (var != null if this were the case), otherwise load null for that arg
                                                mergeIf(lockStateVar != null, () -> new Object[] {
                                                        loadVar(lockStateVar)
                                                }).mergeIf(lockStateVar == null, () -> new Object[] {
                                                        loadNull()
                                                }).generate()
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
    
    private static InsnList saveStateFromInvocationWithinTryCatch(MethodProperties props, int idx) {
        Validate.notNull(props);
        Validate.isTrue(idx >= 0);
        TryCatchInvokeContinuationPoint cp = validateAndGetContinuationPoint(props, idx, TryCatchInvokeContinuationPoint.class);

        Variable contArg = props.getCoreVariables().getContinuationArgVar();
        Variable savedLocalsVar = props.getCoreVariables().getSavedLocalsVar();
        Variable savedStackVar = props.getCoreVariables().getSavedStackVar();

        Variable savedPartialStackVar = props.getCoreVariables().getSavedPartialStackVar();
        Variable savedArgsVar = props.getCoreVariables().getSavedArgumentsVar();
        
        Variable lockStateVar = props.getLockVariables().getLockStateVar();

        Variable throwableVar = props.getCacheVariables().getThrowableCacheVar();

        Type returnType = props.getReturnType();
        
        Frame<BasicValue> frame = cp.getFrame();
        MethodInsnNode invokeNode = cp.getInvokeInstruction();
        LabelNode continueExecLabelNode = cp.getContinueExecutionLabel();
        LabelNode exceptionExecutionLabelNode = cp.getExceptionExecutionLabel();

        int invokeArgCount = getArgumentCountRequiredForInvocation(invokeNode);
        int preInvokeStackSize = frame.getStackSize();
        int postInvokeStackSize = frame.getStackSize() - invokeArgCount;
        return merge(// save args for invoke
                saveOperandStack(savedArgsVar, frame, preInvokeStackSize, invokeArgCount),
                cloneInvokeNode(invokeNode), // invoke method  (ADDED MULTIPLE TIMES -- MUST BE CLONED)
                ifIntegersEqual(// if we're saving after invoke, return dummy value
                        call(CONTINUATION_GETMODE_METHOD, loadVar(contArg)),
                        loadIntConst(MODE_SAVING),
                        merge(popMethodResult(invokeNode),
                                // since we invoked the method before getting here, we already consumed the arguments that were sitting
                                // on the stack waiting to be consumed by the method -- as such, subtract the number of arguments from the
                                // total stack size when saving!!!!! THIS IS SUPER IMPORTANT!!!!
                                saveOperandStack(savedPartialStackVar, frame, postInvokeStackSize, postInvokeStackSize),
                                combineObjectArrays(savedStackVar, savedPartialStackVar, savedArgsVar),
                                saveLocalVariableTable(savedLocalsVar, frame),
                                exitStoredMonitors(props), // inserted many times, must be cloned
                                call(CONTINUATION_ADDPENDING_METHOD, loadVar(contArg),
                                        construct(METHODSTATE_INIT_METHOD,
                                                loadIntConst(idx),
                                                loadVar(savedStackVar),
                                                loadVar(savedLocalsVar),
                                                // load lockstate for last arg if method actually has monitorenter/exit insn in it
                                                // (var != null if this were the case), otherwise load null for that arg
                                                mergeIf(lockStateVar != null, () -> new Object[] {
                                                        loadVar(lockStateVar)
                                                }).mergeIf(lockStateVar == null, () -> new Object[] {
                                                        loadNull()
                                                }).generate()
                                        )
                                ),
                                returnDummy(returnType)
                        )
                ),
                jumpTo(continueExecLabelNode),
                
                
                
                addLabel(exceptionExecutionLabelNode),
                loadVar(throwableVar),
                throwThrowable(),
                
                
                
                addLabel(continueExecLabelNode)
        );
    }
    
    
    
    
    
    
    
    
    
    /**
     * Generates instructions that returns a dummy value. Return values are as follows:
     * <ul>
     * <li>void -&gt; no value</li>
     * <li>boolean -&gt; false</li>
     * <li>byte/short/char/int -&gt; 0</li>
     * <li>long -&gt; 0L</li>
     * <li>float -&gt; 0.0f</li>
     * <li>double -&gt; 0.0</li>
     * <li>Object -&gt; null</li>
     * </ul>
     *
     * @param returnType return type of the method this generated bytecode is for
     * @return instructions to return a dummy value
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code returnType}'s sort is of {@link Type#METHOD}
     */
    private static InsnList returnDummy(Type returnType) {
        Validate.notNull(returnType);
        Validate.isTrue(returnType.getSort() != Type.METHOD);

        InsnList ret = new InsnList();

        switch (returnType.getSort()) {
            case Type.VOID:
                ret.add(new InsnNode(Opcodes.RETURN));
                break;
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.SHORT:
            case Type.CHAR:
            case Type.INT:
                ret.add(new InsnNode(Opcodes.ICONST_0));
                ret.add(new InsnNode(Opcodes.IRETURN));
                break;
            case Type.LONG:
                ret.add(new InsnNode(Opcodes.LCONST_0));
                ret.add(new InsnNode(Opcodes.LRETURN));
                break;
            case Type.FLOAT:
                ret.add(new InsnNode(Opcodes.FCONST_0));
                ret.add(new InsnNode(Opcodes.FRETURN));
                break;
            case Type.DOUBLE:
                ret.add(new InsnNode(Opcodes.DCONST_0));
                ret.add(new InsnNode(Opcodes.DRETURN));
                break;
            case Type.OBJECT:
            case Type.ARRAY:
                ret.add(new InsnNode(Opcodes.ACONST_NULL));
                ret.add(new InsnNode(Opcodes.ARETURN));
                break;
            default:
                throw new IllegalStateException();
        }

        return ret;
    }
    
    /**
     * Generates instructions to pop the result of the method off the stack. This will only generate instructions if the method being
     * invoked generates a return value.
     * @param invokeInsnNode instruction for the method that was invoked (can either be of type {@link MethodInsnNode} or
     * {@link InvokeDynamicInsnNode} -- this is used to determine how many items to pop off the stack
     * @return instructions for a pop (only if the method being invoked generates a return value)
     * @throws IllegalArgumentException if {@code invokeInsnNode} isn't of type {@link MethodInsnNode} or {@link InvokeDynamicInsnNode}
     * @throws NullPointerException if any argument is {@code null}
     */
    private static InsnList popMethodResult(AbstractInsnNode invokeInsnNode) {
        Validate.notNull(invokeInsnNode);
        
        Type returnType = getReturnTypeOfInvocation(invokeInsnNode);
        
        InsnList ret = new InsnList();
        switch (returnType.getSort()) {
            case Type.LONG:
            case Type.DOUBLE:
                ret.add(new InsnNode(Opcodes.POP2));
                break;
            case Type.VOID:
                break;
            case Type.METHOD:
                throw new IllegalStateException(); // this should never happen
            default:
                ret.add(new InsnNode(Opcodes.POP));
                break;
        }

        return ret;
    }
    
    
    
    
    
    
    
    
    
    
    
    
    @SuppressWarnings("unchecked")
    private static <T extends ContinuationPoint> T validateAndGetContinuationPoint(
            MethodProperties props, int idx, Class<T> expectedType) {
        Validate.notNull(props);
        Validate.notNull(expectedType);
        Validate.isTrue(idx >= 0);
        
        List<ContinuationPoint> continuationPoints = props.getContinuationPoints();
        Validate.isTrue(idx < continuationPoints.size());
        
        ContinuationPoint continuationPoint = continuationPoints.get(idx);
        Validate.isTrue(expectedType.isAssignableFrom(continuationPoint.getClass()));
        
        return (T) continuationPoint;
    }
}
