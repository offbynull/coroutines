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

import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.cloneInsnList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;

final class FlowInstrumentationLogic {
    private final InsnList entryPointInsnList;
    private final Map<AbstractInsnNode, InsnList> invokeInsnNodeReplacements;
    private final Set<LabelNode> globalLabelNodes;

    FlowInstrumentationLogic(InsnList entryPointInsnList, Map<AbstractInsnNode, InsnList> invokeInsnNodeReplacements,
            Set<LabelNode> globalLabelNodes) {
        this.entryPointInsnList = entryPointInsnList;
        this.invokeInsnNodeReplacements = invokeInsnNodeReplacements;
        this.globalLabelNodes = globalLabelNodes;
    }

    // Always clone all instruction lists being passed out, because if that instruction list gets added to another instruction list it will
    // be emptied. Subsequent operations that may need to add that same instruction list will silently insert nothing.
    
    InsnList getEntryPointInsnList() {
        return cloneInsnList(entryPointInsnList, globalLabelNodes);
    }

    Map<AbstractInsnNode, InsnList> getInvokeInsnNodeReplacements() {
        return invokeInsnNodeReplacements.entrySet().stream()
                .collect(Collectors.toMap(k -> k.getKey(), v -> cloneInsnList(v.getValue(), globalLabelNodes)));
    }
}
