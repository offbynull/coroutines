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

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

final class ContinuationPoint {

    private boolean suspend;
    private int id;
    private AbstractInsnNode invokeInsnNode;
    private LabelNode restoreLabelNode;
    private Frame frame;

    ContinuationPoint(boolean suspend, int id, AbstractInsnNode invokeInsnNode, Frame<BasicValue> frame) {
        this.suspend = suspend;
        this.id = id;
        this.invokeInsnNode = invokeInsnNode;
        this.frame = frame;
        restoreLabelNode = new LabelNode();
    }

    boolean isSuspend() {
        return suspend;
    }

    int getId() {
        return id;
    }

    AbstractInsnNode getInvokeInsnNode() {
        return invokeInsnNode;
    }

    LabelNode getRestoreLabelNode() {
        return restoreLabelNode;
    }

    Frame getFrame() {
        return frame;
    }

}
