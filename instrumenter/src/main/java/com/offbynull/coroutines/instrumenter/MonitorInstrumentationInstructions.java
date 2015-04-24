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

import java.util.Map;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

final class MonitorInstrumentationInstructions {

    private final Map<AbstractInsnNode, InsnList> monitorInsnNodeReplacements;
    private final InsnList createAndStoreLockStateInsnList;
    private final InsnList loadAndStoreLockStateFromMethodStateInsnList;
    private final InsnList loadLockStateToStackInsnList;
    private final InsnList enterMonitorsInLockStateInsnList;
    private final InsnList exitMonitorsInLockStateInsnList;

    MonitorInstrumentationInstructions(Map<AbstractInsnNode, InsnList> monitorInsnNodeReplacements,
            InsnList createAndStoreLockStateInsnList, InsnList loadAndStoreLockStateFromMethodStateInsnList,
            InsnList loadLockStateToStackInsnList, InsnList enterMonitorsInLockStateInsnList,
            InsnList exitMonitorsInLockStateInsnList) {
        Validate.notNull(monitorInsnNodeReplacements);
        Validate.notNull(createAndStoreLockStateInsnList);
        Validate.notNull(loadAndStoreLockStateFromMethodStateInsnList);
        Validate.notNull(loadLockStateToStackInsnList);
        Validate.notNull(enterMonitorsInLockStateInsnList);
        Validate.notNull(exitMonitorsInLockStateInsnList);
        Validate.noNullElements(monitorInsnNodeReplacements.keySet());
        Validate.noNullElements(monitorInsnNodeReplacements.values());
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
