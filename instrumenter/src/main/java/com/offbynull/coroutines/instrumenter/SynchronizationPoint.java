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

import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

class SynchronizationPoint {

    private final InsnNode monitorInstruction;
    private final Frame<BasicValue> frame;

    SynchronizationPoint(
            InsnNode monitorInstruction,
            Frame<BasicValue> frame) {
        Validate.notNull(monitorInstruction);
        Validate.notNull(frame);
        Validate.isTrue(monitorInstruction.getOpcode() == Opcodes.MONITORENTER || monitorInstruction.getOpcode() == Opcodes.MONITOREXIT);

        this.monitorInstruction = monitorInstruction;
        this.frame = frame;
    }

    public InsnNode getMonitorInstruction() {
        return monitorInstruction;
    }

    public Frame<BasicValue> getFrame() {
        return frame;
    }
}
