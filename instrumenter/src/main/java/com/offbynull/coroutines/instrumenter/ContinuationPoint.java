package com.offbynull.coroutines.instrumenter;

import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

final class ContinuationPoint {

    private boolean suspend;
    private int id;
    private AbstractInsnNode invokeInsnNode;
    private LabelNode restoreLabelNode;
    private Frame frame;

    ContinuationPoint(boolean suspend, int id, AbstractInsnNode invokeInsnNode, Frame<BasicValue> frame, Type methodReturnType) {
        Validate.notNull(invokeInsnNode);
        Validate.notNull(frame);
        Validate.notNull(methodReturnType);
        Validate.isTrue(id >= 0);
        Validate.isTrue(methodReturnType.getSort() != Type.METHOD);
        Validate.isTrue(invokeInsnNode instanceof MethodInsnNode || invokeInsnNode instanceof InvokeDynamicInsnNode);

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
