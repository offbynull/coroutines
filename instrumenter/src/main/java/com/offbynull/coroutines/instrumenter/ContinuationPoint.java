package com.offbynull.coroutines.instrumenter;

import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Type;
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

    public ContinuationPoint(boolean suspend, int id, AbstractInsnNode invokeInsnNode, Frame<BasicValue> frame, Type methodReturnType) {
        Validate.notNull(invokeInsnNode);
        Validate.notNull(frame);
        Validate.notNull(methodReturnType);
        Validate.isTrue(id >= 0);
        Validate.isTrue(methodReturnType.getSort() != Type.METHOD);

        this.suspend = suspend;
        this.id = id;
        this.invokeInsnNode = invokeInsnNode;
        this.frame = frame;
        restoreLabelNode = new LabelNode();
    }

    public boolean isSuspend() {
        return suspend;
    }

    public int getId() {
        return id;
    }

    public AbstractInsnNode getInvokeInsnNode() {
        return invokeInsnNode;
    }

    public LabelNode getRestoreLabelNode() {
        return restoreLabelNode;
    }

    public Frame getFrame() {
        return frame;
    }

}
