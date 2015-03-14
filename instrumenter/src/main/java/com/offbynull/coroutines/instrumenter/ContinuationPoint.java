package com.offbynull.coroutines.instrumenter;

import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

final class ContinuationPoint {

    private boolean yield;
    private int id;
    private AbstractInsnNode invokeInsnNode;
    private LabelNode restoreLabelNode;
    private Frame frame;

    public ContinuationPoint(boolean yield, int id, AbstractInsnNode invokeInsnNode, Frame<BasicValue> frame,
            VariableTable variableTable, Type methodReturnType) {
        Validate.notNull(invokeInsnNode);
        Validate.notNull(frame);
        Validate.notNull(variableTable);
        Validate.notNull(methodReturnType);
        Validate.isTrue(id >= 0);
        Validate.isTrue(methodReturnType.getSort() != Type.METHOD);

        this.yield = yield;
        this.id = id;
        this.frame = frame;
        restoreLabelNode = new LabelNode();
    }

    public boolean isYield() {
        return yield;
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
