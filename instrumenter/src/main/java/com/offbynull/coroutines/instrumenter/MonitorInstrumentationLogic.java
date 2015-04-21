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

import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.call;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.cloneMonitorNode;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.construct;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.empty;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.forEach;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadNull;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadVar;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.merge;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.monitorEnter;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.monitorExit;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.saveVar;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.searchForOpcodes;
import com.offbynull.coroutines.instrumenter.asm.VariableTable;
import com.offbynull.coroutines.user.LockState;
import com.offbynull.coroutines.user.MethodState;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

final class MonitorInstrumentationLogic {

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
    
    private final Map<AbstractInsnNode, InsnList> monitorInsnNodeReplacements;
    private final InsnList createAndStoreLockStateInsnList;
    private final InsnList loadAndStoreLockStateFromMethodStateInsnList;
    private final InsnList loadLockStateToStackInsnList;
    private final InsnList enterMonitorsInLockStateInsnList;
    private final InsnList exitMonitorsInLockStateInsnList;

    static MonitorInstrumentationLogic generate(MethodNode methodNode,
            MonitorInstrumentationVariables monitorInstrumentationVariables) {

        VariableTable.Variable tempObjVar = monitorInstrumentationVariables.getTempObjectVar();
        VariableTable.Variable counterVar = monitorInstrumentationVariables.getCounterVar();
        VariableTable.Variable arrayLenVar = monitorInstrumentationVariables.getArrayLenVar();
        VariableTable.Variable lockStateVar = monitorInstrumentationVariables.getLockStateVar();
        VariableTable.Variable methodStateVar = monitorInstrumentationVariables.getMethodStateVar();
        
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
        
    private MonitorInstrumentationLogic(Map<AbstractInsnNode, InsnList> monitorInsnNodeReplacements,
            InsnList createAndStoreLockStateInsnList, InsnList loadAndStoreLockStateFromMethodStateInsnList,
            InsnList loadLockStateToStackInsnList, InsnList enterMonitorsInLockStateInsnList,
            InsnList exitMonitorsInLockStateInsnList) {
        this.monitorInsnNodeReplacements = monitorInsnNodeReplacements;
        this.createAndStoreLockStateInsnList = createAndStoreLockStateInsnList;
        this.loadAndStoreLockStateFromMethodStateInsnList = loadAndStoreLockStateFromMethodStateInsnList;
        this.loadLockStateToStackInsnList = loadLockStateToStackInsnList;
        this.enterMonitorsInLockStateInsnList = enterMonitorsInLockStateInsnList;
        this.exitMonitorsInLockStateInsnList = exitMonitorsInLockStateInsnList;
    }

    // WARNING: Be careful with using these more than once. If you insert one InsnList in to another InsnList, it'll become empty. If you
    // need to insert the instructions in an InsnList multiple times, make sure to CLONE IT FIRST!
    
    Map<AbstractInsnNode, InsnList> getMonitorInsnNodeReplacements() {
        return monitorInsnNodeReplacements;
    }

    InsnList getCreateAndStoreLockStateInsnList() {
        return createAndStoreLockStateInsnList;
    }

    InsnList getLoadAndStoreLockStateFromMethodStateInsnList() {
        return loadAndStoreLockStateFromMethodStateInsnList;
    }


    InsnList getLoadLockStateToStackInsnList() {
        return loadLockStateToStackInsnList;
    }

    InsnList getEnterMonitorsInLockStateInsnList() {
        return enterMonitorsInLockStateInsnList;
    }

    InsnList getExitMonitorsInLockStateInsnList() {
        return exitMonitorsInLockStateInsnList;
    }

}
