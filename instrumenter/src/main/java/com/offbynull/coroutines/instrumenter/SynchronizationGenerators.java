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

import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.call;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.construct;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.empty;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.forEach;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.loadVar;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.merge;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.saveVar;
import com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable;
import com.offbynull.coroutines.instrumenter.generators.DebugGenerators.MarkerType;
import static com.offbynull.coroutines.instrumenter.generators.DebugGenerators.debugMarker;
import com.offbynull.coroutines.user.LockState;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

final class SynchronizationGenerators {

    private static final Constructor<LockState> LOCKSTATE_INIT_METHOD
            = ConstructorUtils.getAccessibleConstructor(LockState.class);
    private static final Method LOCKSTATE_ENTER_METHOD
            = MethodUtils.getAccessibleMethod(LockState.class, "enter", Object.class);
    private static final Method LOCKSTATE_EXIT_METHOD
            = MethodUtils.getAccessibleMethod(LockState.class, "exit", Object.class);
    private static final Method LOCKSTATE_TOARRAY_METHOD
            = MethodUtils.getAccessibleMethod(LockState.class, "toArray");

    private SynchronizationGenerators() {
        // do nothing
    }
    
    public static InsnList createMonitorContainerAndSaveToVar(MethodProperties props) {
        Validate.notNull(props);
        
        List<SynchronizationPoint> syncPoints = props.getSynchronizationPoints();

        // If we don't have any MONITORENTER/MONITOREXIT instructions in the method, we don't need to create a LockState object to monitors
        // in. There are no monitors being entered/exited to store.

        if (syncPoints.isEmpty()) {
            return empty();
        }

        Variable lockStateVar = props.getLockVariables().getLockStateVar();
        Validate.isTrue(lockStateVar != null);

        MarkerType markerType = props.getDebugMarkerType();

        return merge(
                debugMarker(markerType, "Creating lockstate and storing"),
                construct(LOCKSTATE_INIT_METHOD),
                saveVar(lockStateVar)
        );
    }
    
    public static InsnList enterStoredMonitors(MethodProperties props) {
        Validate.notNull(props);

        List<SynchronizationPoint> syncPoints = props.getSynchronizationPoints();

        // If we don't have any MONITORENTER/MONITOREXIT instructions in the method, we don't need to create a LockState object to monitors
        // in. There are no monitors being entered/exited to store.

        if (syncPoints.isEmpty()) {
            return empty();
        }

        Variable lockStateVar = props.getLockVariables().getLockStateVar();
        Variable counterVar = props.getLockVariables().getCounterVar();
        Variable arrayLenVar = props.getLockVariables().getArrayLenVar();
        Validate.isTrue(lockStateVar != null);
        Validate.isTrue(counterVar != null);
        Validate.isTrue(arrayLenVar != null);
        
        MarkerType markerType = props.getDebugMarkerType();

        return forEach(counterVar, arrayLenVar,
                merge(
                        debugMarker(markerType, "Loading monitors to enter"),
                        call(LOCKSTATE_TOARRAY_METHOD, loadVar(lockStateVar))
                ),
                merge(
                        debugMarker(markerType, "Entering monitor"),
                        new InsnNode(Opcodes.MONITORENTER)
                )
        );
    }
    
    public static InsnList exitStoredMonitors(MethodProperties props) {
        Validate.notNull(props);

        List<SynchronizationPoint> syncPoints = props.getSynchronizationPoints();

        // If we don't have any MONITORENTER/MONITOREXIT instructions in the method, we don't need to create a LockState object to monitors
        // in. There are no monitors being entered/exited to store.

        if (syncPoints.isEmpty()) {
            return empty();
        }

        Variable lockStateVar = props.getLockVariables().getLockStateVar();
        Variable counterVar = props.getLockVariables().getCounterVar();
        Variable arrayLenVar = props.getLockVariables().getArrayLenVar();        
        Validate.isTrue(lockStateVar != null);
        Validate.isTrue(counterVar != null);
        Validate.isTrue(arrayLenVar != null);

        MarkerType markerType = props.getDebugMarkerType();

        return forEach(counterVar, arrayLenVar,
                merge(
                        debugMarker(markerType, "Loading monitors to exit"),
                        call(LOCKSTATE_TOARRAY_METHOD, loadVar(lockStateVar))
                ),
                merge(
                        debugMarker(markerType, "Exitting monitor"),
                        new InsnNode(Opcodes.MONITOREXIT)
                )
        );
    }
    
    public static InsnList enterMonitorAndStore(MethodProperties props) {
        Validate.notNull(props);

        // Doesn't make sense for this to get called if there are no synchronization points
        List<SynchronizationPoint> syncPoints = props.getSynchronizationPoints();
        Validate.isTrue(!syncPoints.isEmpty()); 

        Type clsType = Type.getType(LOCKSTATE_ENTER_METHOD.getDeclaringClass());
        Type methodType = Type.getType(LOCKSTATE_ENTER_METHOD);
        String clsInternalName = clsType.getInternalName();
        String methodDesc = methodType.getDescriptor();
        String methodName = LOCKSTATE_ENTER_METHOD.getName();
        
        Variable lockStateVar = props.getLockVariables().getLockStateVar();
        Validate.isTrue(lockStateVar != null);
        
        MarkerType markerType = props.getDebugMarkerType();

        // NOTE: This adds to the lock state AFTER locking.
        return merge(
                debugMarker(markerType, "Entering monitor and storing"),
                                                                         // [obj]
                new InsnNode(Opcodes.DUP),                               // [obj, obj]
                new InsnNode(Opcodes.MONITORENTER),                      // [obj]
                new VarInsnNode(Opcodes.ALOAD, lockStateVar.getIndex()), // [obj, lockState]
                new InsnNode(Opcodes.SWAP),                              // [lockState, obj]
                new MethodInsnNode(Opcodes.INVOKEVIRTUAL,                // []
                        clsInternalName,
                        methodName,
                        methodDesc,
                        false)
        );
    }
    
    public static InsnList exitMonitorAndDelete(MethodProperties props) {
        Validate.notNull(props);

        // Doesn't make sense for this to get called if there are no synchronization points
        List<SynchronizationPoint> syncPoints = props.getSynchronizationPoints();
        Validate.isTrue(!syncPoints.isEmpty()); 

        Type clsType = Type.getType(LOCKSTATE_EXIT_METHOD.getDeclaringClass());
        Type methodType = Type.getType(LOCKSTATE_EXIT_METHOD);
        String clsInternalName = clsType.getInternalName();
        String methodDesc = methodType.getDescriptor();
        String methodName = LOCKSTATE_EXIT_METHOD.getName();

        Variable lockStateVar = props.getLockVariables().getLockStateVar();
        Validate.isTrue(lockStateVar != null);

        MarkerType markerType = props.getDebugMarkerType();
        
        // NOTE: This removes the lock AFTER unlocking.
        return merge(
                debugMarker(markerType, "Exiting monitor and unstoring"),
                                                                         // [obj]
                new InsnNode(Opcodes.DUP),                               // [obj, obj]
                new InsnNode(Opcodes.MONITOREXIT),                       // [obj]
                new VarInsnNode(Opcodes.ALOAD, lockStateVar.getIndex()), // [obj, lockState]
                new InsnNode(Opcodes.SWAP),                              // [lockState, obj]
                new MethodInsnNode(Opcodes.INVOKEVIRTUAL,                // []
                        clsInternalName,
                        methodName,
                        methodDesc,
                        false)
        );
    }
}
