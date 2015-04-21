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
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

final class FlowInstrumentationInstructions {
    private final InsnList entryPointInsnList;
    private final Map<AbstractInsnNode, InsnList> invokeInsnNodeReplacements;

    FlowInstrumentationInstructions(InsnList entryPointInsnList, Map<AbstractInsnNode, InsnList> invokeInsnNodeReplacements) {
        this.entryPointInsnList = entryPointInsnList;
        this.invokeInsnNodeReplacements = invokeInsnNodeReplacements;
    }
    
    // WARNING: Be careful with using these more than once. If you insert one InsnList in to another InsnList, it'll become empty. If you
    // need to insert the instructions in an InsnList multiple times, make sure to CLONE IT FIRST!
    
    InsnList getEntryPointInsnList() {
        return entryPointInsnList;
    }

    Map<AbstractInsnNode, InsnList> getInvokeInsnNodeReplacements() {
        return invokeInsnNodeReplacements;
    }
}
