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

import static com.offbynull.coroutines.instrumenter.InternalUtils.validateAndGetContinuationPoint;
import static com.offbynull.coroutines.instrumenter.LocalsStateGenerators.loadLocals;
import static com.offbynull.coroutines.instrumenter.LocalsStateGenerators.saveLocals;
import static com.offbynull.coroutines.instrumenter.SynchronizationGenerators.enterStoredMonitors;
import static com.offbynull.coroutines.instrumenter.SynchronizationGenerators.exitStoredMonitors;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.addLabel;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.call;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.cloneInvokeNode;
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
import com.offbynull.coroutines.instrumenter.generators.DebugGenerators.MarkerType;
import static com.offbynull.coroutines.instrumenter.generators.DebugGenerators.debugMarker;
import static com.offbynull.coroutines.instrumenter.SynchronizationGenerators.createMonitorContainer;
import static com.offbynull.coroutines.instrumenter.PackStateGenerators.packStorageArrays;
import static com.offbynull.coroutines.instrumenter.PackStateGenerators.unpackStorageArrays;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.lineNumber;
import static com.offbynull.coroutines.instrumenter.OperandStackStateGenerators.loadOperandStack;
import static com.offbynull.coroutines.instrumenter.OperandStackStateGenerators.saveOperandStack;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.pop;

final class ContinuationGenerators {
    
    private static final Method CONTINUATION_GETMODE_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "getMode");
    private static final Method CONTINUATION_SETMODE_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "setMode", Integer.TYPE);

    // Need a primer on how to handle method states with the Continuation class? There are comments in the Continuation class that describe
    // how things should work.
    private static final Method CONTINUATION_LOADNEXTMETHODSTATE_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "loadNextMethodState");
    private static final Method CONTINUATION_UNLOADCURRENTMETHODSTATE_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "unloadCurrentMethodState");
    private static final Method CONTINUATION_UNLOADMETHODSTATETOBEFORE_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "unloadMethodStateToBefore", MethodState.class);
    private static final Method CONTINUATION_PUSHNEWMETHODSTATE_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "pushNewMethodState", MethodState.class);

    private static final Constructor<MethodState> METHODSTATE_INIT_METHOD
            = ConstructorUtils.getAccessibleConstructor(MethodState.class, Integer.TYPE, Object[].class, LockState.class);
    private static final Method METHODSTATE_GETCONTINUATIONPOINT_METHOD
            = MethodUtils.getAccessibleMethod(MethodState.class, "getContinuationPoint");
    private static final Method METHODSTATE_GETDATA_METHOD
            = MethodUtils.getAccessibleMethod(MethodState.class, "getData");
    private static final Method METHODSTATE_GETLOCKSTATE_METHOD
            = MethodUtils.getAccessibleMethod(MethodState.class, "getLockState");
    
    private ContinuationGenerators() {
        // do nothing
    }
    
    public static InsnList entryPointLoader(MethodAttributes attrs) {
        Validate.notNull(attrs);

        Variable contArg = attrs.getCoreVariables().getContinuationArgVar();
        Variable methodStateVar = attrs.getCoreVariables().getMethodStateVar();
        Variable storageContainerVar = attrs.getStorageContainerVariables().getContainerVar();
        
        Variable lockStateVar = attrs.getLockVariables().getLockStateVar();

        int numOfContinuationPoints = attrs.getContinuationPoints().size();

        MarkerType markerType = attrs.getSettings().getMarkerType();
        String dbgSig = getLogPrefix(attrs);
        
        LabelNode startOfMethodLabelNode = new LabelNode();
        return merge(
                tableSwitch(
                        merge(
                                debugMarker(markerType, dbgSig + "Getting state for switch"),
                                call(CONTINUATION_GETMODE_METHOD, loadVar(contArg))
                        ),
                        merge(
                                debugMarker(markerType, dbgSig + "Unrecognized state"),
                                throwRuntimeException("Unrecognized state")
                        ),
                        0,
                        merge(
                                debugMarker(markerType, dbgSig + "Case 0 -- Fresh invocation"),
                                // create lockstate if method actually has monitorenter/exit in it (var != null if this were the case)
                                mergeIf(lockStateVar != null, () -> new Object[] {
                                        debugMarker(markerType, "Creating monitors container"),
                                        createMonitorContainer(attrs),
                                }),
                                debugMarker(markerType, dbgSig + "Jump to start of method point"),
                                jumpTo(startOfMethodLabelNode)
                        ),
                        merge(
                                debugMarker(markerType, dbgSig + "Case 1 -- Saving state"),
                                throwRuntimeException("Unexpected state (saving not allowed at this point)")
                        ),
                        merge(
                                debugMarker(markerType, dbgSig + "Case 2 -- Loading state"),
                                debugMarker(markerType, dbgSig + "Loading method state"),
                                call(CONTINUATION_LOADNEXTMETHODSTATE_METHOD, loadVar(contArg)),
                                saveVar(methodStateVar),
                                debugMarker(markerType, dbgSig + "Getting method state data"),
                                call(METHODSTATE_GETDATA_METHOD, loadVar(methodStateVar)),
                                saveVar(storageContainerVar),
                                // get lockstate if method actually has monitorenter/exit in it (var != null if this were the case)
                                mergeIf(lockStateVar != null, () -> new Object[] {
                                        debugMarker(markerType, dbgSig + "Method has synch points, so loading lockstate as well"),
                                        call(METHODSTATE_GETLOCKSTATE_METHOD, loadVar(methodStateVar)),
                                        saveVar(lockStateVar)
                                }),
                                tableSwitch(
                                        merge(
                                                debugMarker(markerType, dbgSig + "Getting continuation id for switch"),
                                                call(METHODSTATE_GETCONTINUATIONPOINT_METHOD, loadVar(methodStateVar))
                                        ),
                                        merge(
                                                debugMarker(markerType, dbgSig + "Unrecognized continuation id"),
                                                throwRuntimeException("Unrecognized continuation id")
                                        ),
                                        0,
                                        IntStream.range(0, numOfContinuationPoints)
                                                .mapToObj(idx -> restoreState(attrs, idx))
                                                .toArray((x) -> new InsnList[x])
                                )
                                // jump to not required here, switch above either throws exception or jumps to restore point
                        )
                ),
                addLabel(startOfMethodLabelNode),
                debugMarker(markerType, dbgSig + "Starting method...")
        );
    }




    public static InsnList restoreState(MethodAttributes attrs, int idx) {
        Validate.notNull(attrs);
        Validate.isTrue(idx >= 0);
        ContinuationPoint continuationPoint = validateAndGetContinuationPoint(attrs, idx, ContinuationPoint.class);
                
        InsnList restoreInsnList;
        if (continuationPoint instanceof SuspendContinuationPoint) {
            restoreInsnList = restoreStateFromSuspend(attrs, idx);
        } else if (continuationPoint instanceof NormalInvokeContinuationPoint) {
            restoreInsnList = restoreStateFromNormalInvocation(attrs, idx);
        } else if (continuationPoint instanceof TryCatchInvokeContinuationPoint) {
            restoreInsnList = restoreStateFromInvocationWithinTryCatch(attrs, idx);
        } else {
            throw new IllegalArgumentException(); // should never happen
        }
        
        return restoreInsnList;
    }
    
    private static InsnList restoreStateFromSuspend(MethodAttributes attrs, int idx) {
        Validate.notNull(attrs);
        Validate.isTrue(idx >= 0);
        SuspendContinuationPoint cp = validateAndGetContinuationPoint(attrs, idx, SuspendContinuationPoint.class);
        
        Integer lineNumber = cp.getLineNumber();

        Variable contArg = attrs.getCoreVariables().getContinuationArgVar();
        Variable methodStateVar = attrs.getCoreVariables().getMethodStateVar();
        StorageVariables savedLocalsVars = attrs.getLocalsStorageVariables();
        StorageVariables savedStackVars = attrs.getStackStorageVariables();
        
        Variable storageContainerVar = attrs.getStorageContainerVariables().getContainerVar();
        
        Variable lockStateVar = attrs.getLockVariables().getLockStateVar();
        
        Frame<BasicValue> frame = cp.getFrame();
        LabelNode continueExecLabelNode = cp.getContinueExecutionLabel();
        
        MarkerType markerType = attrs.getSettings().getMarkerType();
        String dbgSig = getLogPrefix(attrs);
        
        //          enterLocks(lockState);
        //          restoreOperandStack(stack);
        //          restoreLocalsStack(localVars);
        //          continuation.setMode(MODE_NORMAL);
        //          goto restorePoint_<number>_continue;
        return merge(
                debugMarker(markerType, dbgSig + "Restoring SUSPEND " + idx),
                debugMarker(markerType, dbgSig + "Unpacking method state data"),
                unpackStorageArrays(markerType, frame, storageContainerVar, savedLocalsVars, savedStackVars),
                debugMarker(markerType, dbgSig + "Restoring operand stack"),
                loadOperandStack(markerType, savedStackVars, frame),
                debugMarker(markerType, dbgSig + "Restoring locals"),
                loadLocals(markerType, savedLocalsVars, frame),
                mergeIf(lineNumber != null, () -> new Object[] {
                    // We add the line number AFTER locals have been restored, so if you put in a break point at the specified line number
                    // the local vars will all show up.
                    lineNumber(lineNumber)
                }),
                // attempt to enter monitors only if method has monitorenter/exit in it (var != null if this were the case)
                mergeIf(lockStateVar != null, () -> new Object[]{
                        debugMarker(markerType, dbgSig + "Entering monitors"),
                        enterStoredMonitors(attrs),
                }),
                debugMarker(markerType, dbgSig + "Popping off continuation object from operand stack"),
                pop(), // frame at the time of invocation to Continuation.suspend() has Continuation reference on the
                       // stack that would have been consumed by that invocation... since we're removing that call, we
                       // also need to pop the Continuation reference from the stack... it's important that we
                       // explicitly do it at this point becuase during loading the stack will be restored with top
                       // of stack pointing to that continuation object
                debugMarker(markerType, dbgSig + "Setting mode to normal"),
                call(CONTINUATION_SETMODE_METHOD, loadVar(contArg), loadIntConst(MODE_NORMAL)),
                // We've successfully completed our restore and we're continuing the invocation, so we need "discard" this method state
                debugMarker(markerType, dbgSig + "Discarding saved method state"),
                call(CONTINUATION_UNLOADCURRENTMETHODSTATE_METHOD, loadVar(contArg)),
                debugMarker(markerType, dbgSig + "Restore complete. Jumping to post-invocation point"),
                jumpTo(continueExecLabelNode)
        );
    }
    
    private static InsnList restoreStateFromNormalInvocation(MethodAttributes attrs, int idx) {
        Validate.notNull(attrs);
        Validate.isTrue(idx >= 0);
        NormalInvokeContinuationPoint cp = validateAndGetContinuationPoint(attrs, idx, NormalInvokeContinuationPoint.class);
        
        Integer lineNumber = cp.getLineNumber();
        
        Variable contArg = attrs.getCoreVariables().getContinuationArgVar();
        StorageVariables savedLocalsVars = attrs.getLocalsStorageVariables();
        StorageVariables savedStackVars = attrs.getStackStorageVariables();
        
        Variable storageContainerVar = attrs.getStorageContainerVariables().getContainerVar();
        
        Variable lockStateVar = attrs.getLockVariables().getLockStateVar();
        
        Type returnType = attrs.getSignature().getReturnType();
        
        Frame<BasicValue> frame = cp.getFrame();
        MethodInsnNode invokeNode = cp.getInvokeInstruction();
        LabelNode continueExecLabelNode = cp.getContinueExecutionLabel();
        
        Type invokeReturnType = getReturnTypeOfInvocation(invokeNode);        
        int invokeArgCount = getArgumentCountRequiredForInvocation(invokeNode);
        
        Variable returnCacheVar = attrs.getCacheVariables().getReturnCacheVar(invokeReturnType); // will be null if void
        
        MarkerType markerType = attrs.getSettings().getMarkerType();
        boolean debugMode = attrs.getSettings().isDebugMode();
        String dbgSig = getLogPrefix(attrs);
        
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
                debugMarker(markerType, dbgSig + "Restoring INVOKE " + idx),
                debugMarker(markerType, dbgSig + "Unpacking method state data"),
                unpackStorageArrays(markerType, frame, storageContainerVar, savedLocalsVars, savedStackVars),
                // attempt to enter monitors only if method has monitorenter/exit in it (var != null if this were the case)
                mergeIf(lockStateVar != null, () -> new Object[]{
                    debugMarker(markerType, dbgSig + "Entering monitors"),
                    enterStoredMonitors(attrs), // we MUST re-enter montiors before going further
                }),
                debugMarker(markerType, dbgSig + "Restoring top " + invokeArgCount + " items of operand stack (just enough to invoke)"),
                loadOperandStack(markerType, savedStackVars, frame, 0, frame.getStackSize() - invokeArgCount, invokeArgCount),
                mergeIf(debugMode, () -> new Object[]{
                    // If in debug mode, load up the locals. This is useful if you're stepping through your coroutine in a debugger... you
                    // can look at method frames above the current one and introspect the variables (what the user expects if they're
                    // running in a debugger).
                    debugMarker(markerType, dbgSig + "Restoring locals (for the purpose of introspecting locals if being run in debugger)"),
                    loadLocals(markerType, savedLocalsVars, frame),
                }),
                mergeIf(lineNumber != null, () -> new Object[]{
                    // We add the line number AFTER locals have been restored, so if you put in a break point at the specified line number
                    // the local vars will all show up (REMEMBER: they'll show up only if debugMode is set).
                    lineNumber(lineNumber)
                }),
                debugMarker(markerType, dbgSig + "Invoking"),
                cloneInvokeNode(invokeNode), // invoke method  (ADDED MULTIPLE TIMES -- MUST BE CLONED)
                ifIntegersEqual(// if we're saving after invoke, return dummy value
                        call(CONTINUATION_GETMODE_METHOD, loadVar(contArg)),
                        loadIntConst(MODE_SAVING),
                        merge(
                                debugMarker(markerType, dbgSig + "Mode set to save on return"),
                                debugMarker(markerType, dbgSig + "Popping dummy return value off stack"),
                                popMethodResult(invokeNode),
                                // attempt to exit monitors only if method has monitorenter/exit in it (var != null if this were the case)
                                mergeIf(lockStateVar != null, () -> new Object[]{
                                    debugMarker(markerType, dbgSig + "Exiting monitors"),
                                    exitStoredMonitors(attrs),
                                }),
                                debugMarker(markerType, dbgSig + "Returning (dummy return value if not void)"),
                                returnDummy(returnType)
                        )
                ),
                mergeIf(returnCacheVar != null, () -> new Object[] {// save return (if returnCacheVar is null means ret type is void)
                    debugMarker(markerType, dbgSig + "Saving invocation return value"),
                    saveVar(returnCacheVar)
                }),
                debugMarker(markerType, dbgSig + "Restoring operand stack (without invoke args)"),
                loadOperandStack(markerType, savedStackVars, frame, 0, 0, frame.getStackSize() - invokeArgCount),
                debugMarker(markerType, dbgSig + "Restoring locals"),
                loadLocals(markerType, savedLocalsVars, frame),
                mergeIf(returnCacheVar != null, () -> new Object[] {// load return (if returnCacheVar is null means ret type is void)
                    debugMarker(markerType, dbgSig + "Loading invocation return value"),
                    loadVar(returnCacheVar)
                }),
                // We've successfully completed our restore and we're continuing the invocation, so we need "discard" this method state
                debugMarker(markerType, dbgSig + "Discarding saved method state"),
                call(CONTINUATION_UNLOADCURRENTMETHODSTATE_METHOD, loadVar(contArg)),
                debugMarker(markerType, dbgSig + "Restore complete. Jumping to post-invocation point"),
                jumpTo(continueExecLabelNode)
        );
    }
    
    private static InsnList restoreStateFromInvocationWithinTryCatch(MethodAttributes attrs, int idx) {
        Validate.notNull(attrs);
        Validate.isTrue(idx >= 0);
        TryCatchInvokeContinuationPoint cp = validateAndGetContinuationPoint(attrs, idx, TryCatchInvokeContinuationPoint.class);
        
        Integer lineNumber = cp.getLineNumber();
        
        Variable contArg = attrs.getCoreVariables().getContinuationArgVar();
        Variable methodStateVar = attrs.getCoreVariables().getMethodStateVar();
        StorageVariables savedLocalsVars = attrs.getLocalsStorageVariables();
        StorageVariables savedStackVars = attrs.getStackStorageVariables();
        
        Variable storageContainerVar = attrs.getStorageContainerVariables().getContainerVar();

        Variable lockStateVar = attrs.getLockVariables().getLockStateVar();
        
        Variable throwableVar = attrs.getCacheVariables().getThrowableCacheVar();
        
        Type returnType = attrs.getSignature().getReturnType();
        
        // tryCatchBlock() invocation further on in this method will populate TryCatchBlockNode fields
        TryCatchBlockNode newTryCatchBlockNode = cp.getTryCatchBlock();

        Frame<BasicValue> frame = cp.getFrame();
        MethodInsnNode invokeNode = cp.getInvokeInstruction();
        LabelNode continueExecLabelNode = cp.getContinueExecutionLabel();
        LabelNode exceptionExecutionLabelNode = cp.getExceptionExecutionLabel();
        
        Type invokeReturnType = getReturnTypeOfInvocation(invokeNode);        
        int invokeArgCount = getArgumentCountRequiredForInvocation(invokeNode);
        
        Variable returnCacheVar = attrs.getCacheVariables().getReturnCacheVar(invokeReturnType); // will be null if void
        
        MarkerType markerType = attrs.getSettings().getMarkerType();
        boolean debugMode = attrs.getSettings().isDebugMode();
        String dbgSig = getLogPrefix(attrs);
        
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
                debugMarker(markerType, dbgSig + "Restoring INVOKE WITHIN TRYCATCH " + idx),
                debugMarker(markerType, dbgSig + "Unpacking method state data"),
                unpackStorageArrays(markerType, frame, storageContainerVar, savedLocalsVars, savedStackVars),
                // attempt to enter monitors only if method has monitorenter/exit in it (var != null if this were the case)
                mergeIf(lockStateVar != null, () -> new Object[]{
                    debugMarker(markerType, dbgSig + "Entering monitors"),
                    enterStoredMonitors(attrs), // we MUST re-enter montiors before going further
                }),
                debugMarker(markerType, dbgSig + "Restoring top " + invokeArgCount + " items of operand stack (just enough to invoke)"),
                loadOperandStack(markerType, savedStackVars, frame, 0, frame.getStackSize() - invokeArgCount, invokeArgCount),
                mergeIf(debugMode, () -> new Object[]{
                    // If in debug mode, load up the locals. This is useful if you're stepping through your coroutine in a debugger... you
                    // can look at method frames above the current one and introspect the variables (what the user expects if they're
                    // running in a debugger).
                    debugMarker(markerType, dbgSig + "Restoring locals (for the purpose of introspecting locals if being run in debugger)"),
                    loadLocals(markerType, savedLocalsVars, frame),
                }),
                mergeIf(lineNumber != null, () -> new Object[]{
                    // We add the line number AFTER locals have been restored, so if you put in a break point at the specified line number
                    // the local vars will all show up (REMEMBER: they'll show up only if debugMode is set).
                    lineNumber(lineNumber)
                }),
                tryCatchBlock(newTryCatchBlockNode,
                        null,
                        merge(// try
                                debugMarker(markerType, dbgSig + "Invoking (within custom try-catch)"),
                                cloneInvokeNode(invokeNode) // invoke method  (ADDED MULTIPLE TIMES -- MUST BE CLONED)
                        ),
                        merge(// catch(any)
                                debugMarker(markerType, dbgSig + "Throwable caught"),
                                debugMarker(markerType, dbgSig + "Saving caught throwable"),
                                saveVar(throwableVar),
                                debugMarker(markerType, dbgSig + "Restoring operand stack (without invoke args)"),
                                loadOperandStack(markerType, savedStackVars, frame, 0, 0, frame.getStackSize() - invokeArgCount),
                                debugMarker(markerType, dbgSig + "Restoring locals"),
                                loadLocals(markerType, savedLocalsVars, frame),
                                // We caught an exception, which means that everything that was invoked after us is pretty much gone and
                                // we're continuing the invocation as if we restore, we need to "discard" this method state along with
                                // everything after it.
                                debugMarker(markerType, dbgSig + "Discarding saved method states up until this point (unwinding)"),
                                call(CONTINUATION_UNLOADMETHODSTATETOBEFORE_METHOD, loadVar(contArg), loadVar(methodStateVar)),
                                debugMarker(markerType, dbgSig + "Restore complete. Jumping to rethrow point (within orig trycatch block)"),
                                jumpTo(exceptionExecutionLabelNode)
                        )
                ),
                ifIntegersEqual(// if we're saving after invoke, return dummy value
                        call(CONTINUATION_GETMODE_METHOD, loadVar(contArg)),
                        loadIntConst(MODE_SAVING),
                        merge(
                                debugMarker(markerType, dbgSig + "Mode set to save on return"),
                                debugMarker(markerType, dbgSig + "Popping dummy return value off stack"),
                                popMethodResult(invokeNode),
                                // attempt to exit monitors only if method has monitorenter/exit in it (var != null if this were the case)
                                mergeIf(lockStateVar != null, () -> new Object[]{
                                    debugMarker(markerType, dbgSig + "Exiting monitors"),
                                    exitStoredMonitors(attrs),
                                }),
                                debugMarker(markerType, dbgSig + "Returning (dummy return value if not void)"),
                                returnDummy(returnType)
                        )
                ),
                mergeIf(returnCacheVar != null, () -> new Object[] {// save return (if returnCacheVar is null means ret type is void)
                    debugMarker(markerType, dbgSig + "Saving invocation return value"),
                    saveVar(returnCacheVar)
                }),
                debugMarker(markerType, dbgSig + "Restoring operand stack (without invoke args)"),
                loadOperandStack(markerType, savedStackVars, frame, 0, 0, frame.getStackSize() - invokeArgCount),
                debugMarker(markerType, dbgSig + "Restoring locals"),
                loadLocals(markerType, savedLocalsVars, frame),
                mergeIf(returnCacheVar != null, () -> new Object[] {// load return (if returnCacheVar is null means ret type is void)
                    debugMarker(markerType, dbgSig + "Loading invocation return value"),
                    loadVar(returnCacheVar)
                }),
                debugMarker(markerType, dbgSig + "Discarding saved method state"),
                call(CONTINUATION_UNLOADCURRENTMETHODSTATE_METHOD, loadVar(contArg)),
                debugMarker(markerType, dbgSig + "Restore complete. Jumping to post-invocation point"),
                jumpTo(continueExecLabelNode)
        );
    }

    
    
    
    
    
    

    

    
    
    
    
    public static InsnList saveState(MethodAttributes attrs, int idx) {
        Validate.notNull(attrs);
        Validate.isTrue(idx >= 0);
        ContinuationPoint continuationPoint = validateAndGetContinuationPoint(attrs, idx, ContinuationPoint.class);
        
        
                
        InsnList saveInsnList;
        if (continuationPoint instanceof SuspendContinuationPoint) {
            saveInsnList = saveStateFromSuspend(attrs, idx);
        } else if (continuationPoint instanceof NormalInvokeContinuationPoint) {
            saveInsnList = saveStateFromNormalInvocation(attrs, idx);
        } else if (continuationPoint instanceof TryCatchInvokeContinuationPoint) {
            saveInsnList = saveStateFromInvocationWithinTryCatch(attrs, idx);
        } else {
            throw new IllegalArgumentException(); // should never happen
        }
        
        return saveInsnList;
    }
    
    private static InsnList saveStateFromSuspend(MethodAttributes attrs, int idx) {
        Validate.notNull(attrs);
        Validate.isTrue(idx >= 0);
        SuspendContinuationPoint cp = validateAndGetContinuationPoint(attrs, idx, SuspendContinuationPoint.class);
        
        Integer lineNumber = cp.getLineNumber();

        Variable contArg = attrs.getCoreVariables().getContinuationArgVar();
        StorageVariables savedLocalsVars = attrs.getLocalsStorageVariables();
        StorageVariables savedStackVars = attrs.getStackStorageVariables();
        Variable storageContainerVar = attrs.getStorageContainerVariables().getContainerVar();
        
        Variable lockStateVar = attrs.getLockVariables().getLockStateVar();
        
        Type returnType = attrs.getSignature().getReturnType();
        
        Frame<BasicValue> frame = cp.getFrame();
        LabelNode continueExecLabelNode = cp.getContinueExecutionLabel();
        
        MarkerType markerType = attrs.getSettings().getMarkerType();
        String dbgSig = getLogPrefix(attrs);
        
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
                mergeIf(lineNumber != null, () -> new Object[]{
                    lineNumber(lineNumber)
                }),
                debugMarker(markerType, dbgSig + "Saving SUSPEND " + idx),
                debugMarker(markerType, dbgSig + "Saving operand stack"),
                saveOperandStack(markerType, savedStackVars, frame), // REMEMBER: STACK IS TOTALLY EMPTY AFTER THIS. ALSO, DON'T FORGET THAT
                                                                     // Continuation OBJECT WILL BE TOP ITEM, NEEDS TO BE DISCARDED ON LOAD
                debugMarker(markerType, dbgSig + "Saving locals"),
                saveLocals(markerType, savedLocalsVars, frame),
                debugMarker(markerType, dbgSig + "Packing locals and operand stack in to container"),
                packStorageArrays(markerType, frame, storageContainerVar, savedLocalsVars, savedStackVars),
                debugMarker(markerType, dbgSig + "Creating and pushing method state"),
                call(CONTINUATION_PUSHNEWMETHODSTATE_METHOD, loadVar(contArg),
                        construct(METHODSTATE_INIT_METHOD,
                                loadIntConst(idx),
                                loadVar(storageContainerVar),
                                // load lockstate for last arg if method actually has monitorenter/exit in it
                                // (var != null if this were the case), otherwise load null for that arg
                                mergeIf(lockStateVar != null, () -> new Object[] {
                                    loadVar(lockStateVar)
                                }).mergeIf(lockStateVar == null, () -> new Object[] {
                                    loadNull()
                                }).generate()
                        )
                ),
                debugMarker(markerType, dbgSig + "Setting mode to save"),
                call(CONTINUATION_SETMODE_METHOD, loadVar(contArg), loadIntConst(MODE_SAVING)),
                // attempt to exit monitors only if method has monitorenter/exit in it (var != null if this were the case)
                mergeIf(lockStateVar != null, () -> new Object[]{
                    debugMarker(markerType, dbgSig + "Exiting monitors"),
                    exitStoredMonitors(attrs),
                }),
                debugMarker(markerType, dbgSig + "Returning (dummy return value if not void)"),
                returnDummy(returnType), // return dummy value
                
                
                
                addLabel(continueExecLabelNode),
                debugMarker(markerType, dbgSig + "Continuing execution...")
        );
    }
    
    private static InsnList saveStateFromNormalInvocation(MethodAttributes attrs, int idx) {
        Validate.notNull(attrs);
        Validate.isTrue(idx >= 0);
        NormalInvokeContinuationPoint cp = validateAndGetContinuationPoint(attrs, idx, NormalInvokeContinuationPoint.class);
        
        Integer lineNumber = cp.getLineNumber();

        Variable contArg = attrs.getCoreVariables().getContinuationArgVar();
        StorageVariables savedLocalsVars = attrs.getLocalsStorageVariables();
        StorageVariables savedStackVars = attrs.getStackStorageVariables();
        Variable storageContainerVar = attrs.getStorageContainerVariables().getContainerVar();
        
        Variable lockStateVar = attrs.getLockVariables().getLockStateVar();

        Type returnType = attrs.getSignature().getReturnType();
        
        Frame<BasicValue> frame = cp.getFrame();
        MethodInsnNode invokeNode = cp.getInvokeInstruction();
        LabelNode continueExecLabelNode = cp.getContinueExecutionLabel();
        
        MarkerType markerType = attrs.getSettings().getMarkerType();
        String dbgSig = getLogPrefix(attrs);
        
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
        return merge(
                mergeIf(lineNumber != null, () -> new Object[]{
                    lineNumber(lineNumber)
                }),
                debugMarker(markerType, dbgSig + "Saving INVOKE " + idx),
                debugMarker(markerType, dbgSig + "Saving top " + invokeArgCount + " items of operand stack (args for invoke)"),
                saveOperandStack(markerType, savedStackVars, frame, invokeArgCount),
                debugMarker(markerType, dbgSig + "Reloading invoke arguments back on to the stack (for invoke)"),
                loadOperandStack(markerType, savedStackVars, frame,
                        frame.getStackSize() - invokeArgCount,
                        frame.getStackSize() - invokeArgCount,
                        invokeArgCount),
                debugMarker(markerType, dbgSig + "Invoking"),
                cloneInvokeNode(invokeNode), // invoke method  (ADDED MULTIPLE TIMES -- MUST BE CLONED)
                ifIntegersEqual(// if we're saving after invoke
                        call(CONTINUATION_GETMODE_METHOD, loadVar(contArg)),
                        loadIntConst(MODE_SAVING),
                        merge(
                                debugMarker(markerType, dbgSig + "Mode set to save on return"),
                                debugMarker(markerType, dbgSig + "Popping dummy return value off stack"),
                                popMethodResult(invokeNode),
                                debugMarker(markerType, dbgSig + "Reloading invoke arguments back on to the stack (for full save)"),
                                loadOperandStack(markerType, savedStackVars, frame,
                                        frame.getStackSize() - invokeArgCount,
                                        frame.getStackSize() - invokeArgCount,
                                        invokeArgCount),
                                debugMarker(markerType, dbgSig + "Saving operand stack"),
                                saveOperandStack(markerType, savedStackVars, frame), // REMEMBER: STACK IS TOTALLY EMPTY AFTER THIS
                                debugMarker(markerType, dbgSig + "Saving locals"),
                                saveLocals(markerType, savedLocalsVars, frame),
                                debugMarker(markerType, dbgSig + "Packing locals and operand stack in to container"),
                                packStorageArrays(markerType, frame, storageContainerVar, savedLocalsVars, savedStackVars),
                                // attempt to exit monitors only if method has monitorenter/exit in it (var != null if this were the case)
                                mergeIf(lockStateVar != null, () -> new Object[]{
                                    debugMarker(markerType, dbgSig + "Exiting monitors"),
                                    exitStoredMonitors(attrs),
                                }),
                                debugMarker(markerType, dbgSig + "Creating and pushing method state"),
                                call(CONTINUATION_PUSHNEWMETHODSTATE_METHOD, loadVar(contArg),
                                        construct(METHODSTATE_INIT_METHOD,
                                                loadIntConst(idx),
                                                loadVar(storageContainerVar),
                                                // load lockstate for last arg if method actually has monitorenter/exit in it
                                                // (var != null if this were the case), otherwise load null for that arg
                                                mergeIf(lockStateVar != null, () -> new Object[] {
                                                    loadVar(lockStateVar)
                                                }).mergeIf(lockStateVar == null, () -> new Object[] {
                                                    loadNull()
                                                }).generate()
                                        )
                                ),
                                debugMarker(markerType, dbgSig + "Returning (dummy return value if not void)"),
                                returnDummy(returnType)
                        )
                ),      

                
                
                
                addLabel(continueExecLabelNode),
                debugMarker(markerType, dbgSig + "Continuing execution...")
        );
    }
    
    private static InsnList saveStateFromInvocationWithinTryCatch(MethodAttributes attrs, int idx) {
        Validate.notNull(attrs);
        Validate.isTrue(idx >= 0);
        TryCatchInvokeContinuationPoint cp = validateAndGetContinuationPoint(attrs, idx, TryCatchInvokeContinuationPoint.class);
        
        Integer lineNumber = cp.getLineNumber();

        Variable contArg = attrs.getCoreVariables().getContinuationArgVar();
        StorageVariables savedLocalsVars = attrs.getLocalsStorageVariables();
        StorageVariables savedStackVars = attrs.getStackStorageVariables();
        Variable storageContainerVar = attrs.getStorageContainerVariables().getContainerVar();
        
        Variable lockStateVar = attrs.getLockVariables().getLockStateVar();

        Variable throwableVar = attrs.getCacheVariables().getThrowableCacheVar();

        Type returnType = attrs.getSignature().getReturnType();
        
        Frame<BasicValue> frame = cp.getFrame();
        MethodInsnNode invokeNode = cp.getInvokeInstruction();
        LabelNode continueExecLabelNode = cp.getContinueExecutionLabel();
        LabelNode exceptionExecutionLabelNode = cp.getExceptionExecutionLabel();
        
        MarkerType markerType = attrs.getSettings().getMarkerType();
        String dbgSig = getLogPrefix(attrs);

        int invokeArgCount = getArgumentCountRequiredForInvocation(invokeNode);
        return merge(
                mergeIf(lineNumber != null, () -> new Object[]{
                    lineNumber(lineNumber)
                }),
                debugMarker(markerType, dbgSig + "Saving INVOKE WITHIN TRYCATCH " + idx),
                debugMarker(markerType, dbgSig + "Saving top " + invokeArgCount + " items of operand stack (args for invoke)"),
                saveOperandStack(markerType, savedStackVars, frame, invokeArgCount),
                debugMarker(markerType, dbgSig + "Reloading invoke arguments back on to the stack (for invoke)"),
                loadOperandStack(markerType, savedStackVars, frame,
                        frame.getStackSize() - invokeArgCount,
                        frame.getStackSize() - invokeArgCount,
                        invokeArgCount),
                debugMarker(markerType, dbgSig + "Invoking"),
                cloneInvokeNode(invokeNode), // invoke method  (ADDED MULTIPLE TIMES -- MUST BE CLONED)
                ifIntegersEqual(// if we're saving after invoke, return dummy value
                        call(CONTINUATION_GETMODE_METHOD, loadVar(contArg)),
                        loadIntConst(MODE_SAVING),
                        merge(debugMarker(markerType, dbgSig + "Mode set to save on return"),
                                debugMarker(markerType, dbgSig + "Popping dummy return value off stack"),
                                popMethodResult(invokeNode),
                                debugMarker(markerType, dbgSig + "Reloading invoke arguments back on to the stack"),
                                loadOperandStack(markerType, savedStackVars, frame,
                                        frame.getStackSize() - invokeArgCount,
                                        frame.getStackSize() - invokeArgCount,
                                        invokeArgCount),
                                debugMarker(markerType, dbgSig + "Saving operand stack"),
                                saveOperandStack(markerType, savedStackVars, frame), // REMEMBER: STACK IS TOTALLY EMPTY AFTER THIS
                                debugMarker(markerType, dbgSig + "Saving locals"),
                                saveLocals(markerType, savedLocalsVars, frame),
                                debugMarker(markerType, dbgSig + "Packing locals and operand stack in to container"),
                                packStorageArrays(markerType, frame, storageContainerVar, savedLocalsVars, savedStackVars),
                                // attempt to exit monitors only if method has monitorenter/exit in it (var != null if this were the case)
                                mergeIf(lockStateVar != null, () -> new Object[]{
                                    debugMarker(markerType, dbgSig + "Exiting monitors"),
                                    exitStoredMonitors(attrs),
                                }),
                                debugMarker(markerType, dbgSig + "Creating and pushing method state"),
                                call(CONTINUATION_PUSHNEWMETHODSTATE_METHOD, loadVar(contArg),
                                        construct(METHODSTATE_INIT_METHOD,
                                                loadIntConst(idx),
                                                loadVar(storageContainerVar),
                                                // load lockstate for last arg if method actually has monitorenter/exit in it
                                                // (var != null if this were the case), otherwise load null for that arg
                                                mergeIf(lockStateVar != null, () -> new Object[] {
                                                    loadVar(lockStateVar)
                                                }).mergeIf(lockStateVar == null, () -> new Object[] {
                                                    loadNull()
                                                }).generate()
                                        )
                                ),
                                debugMarker(markerType, dbgSig + "Returning (dummy return value if not void)"),
                                returnDummy(returnType)
                        )
                ),
                debugMarker(markerType, dbgSig + "Jumping to continue execution point..."),
                jumpTo(continueExecLabelNode),
                
                
                
                addLabel(exceptionExecutionLabelNode),
                // Since we're rethrowing from original try/catch, if the throwable is of the expected type it'll get handled. If not it'll
                // get thrown up the chain (which is normal behaviour).
                debugMarker(markerType, dbgSig + "Rethrowing throwable from original try/catch"),
                loadVar(throwableVar),
                throwThrowable(),
                
                
                
                addLabel(continueExecLabelNode),
                debugMarker(markerType, dbgSig + "Continuing execution...")
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
    
    private static String getLogPrefix(MethodAttributes attrs) {
        return attrs.getSignature().getClassName() + "-"
                + attrs.getSignature().getMethodName() + "-"
                + attrs.getSignature().getMethodDescriptor() + " >>> ";
    }
}
