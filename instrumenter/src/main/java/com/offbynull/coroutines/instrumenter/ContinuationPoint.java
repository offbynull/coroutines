package com.offbynull.coroutines.instrumenter;

import static com.offbynull.coroutines.instrumenter.InstructionUtils.addLabel;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.empty;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.invokePopMethodState;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.invokePushMethodState;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.jumpTo;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.loadLocalVariableTable;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.loadOperandStack;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.merge;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.returnDummy;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.saveLocalVariableTable;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.saveOperandStack;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

final class ContinuationPoint {

    private boolean yield;
    private int id;
    private AbstractInsnNode invokeInsnNode;
    private InsnList restoreInsnList;
    private InsnList storeInsnList;

    public ContinuationPoint(boolean yield, int id, AbstractInsnNode invokeInsnNode, Frame<BasicValue> frame,
            VariableTable variableTable, Type methodReturnType) {
        Validate.isTrue(id >= 0);

        this.yield = yield;
        this.id = id;

        LabelNode restoreLabelNode = new LabelNode();
        
        restoreInsnList
                = merge(
                        invokePopMethodState(
                                variableTable.getContinuationIndex(),
                                variableTable.getOperandStackArrayIndex(),
                                variableTable.getLocalVarTableArrayIndex(),
                                variableTable.getTempObjectIndex()),
                        loadOperandStack(
                                variableTable.getOperandStackArrayIndex(),
                                variableTable.getTempObjectIndex(),
                                frame),
                        loadLocalVariableTable(
                                variableTable.getLocalVarTableArrayIndex(),
                                variableTable.getTempObjectIndex(),
                                frame),
                        jumpTo(restoreLabelNode)
                );
        storeInsnList
                = merge(
                        saveOperandStack(
                                variableTable.getOperandStackArrayIndex(),
                                variableTable.getTempObjectIndex(),
                                frame),
                        saveLocalVariableTable(
                                variableTable.getLocalVarTableArrayIndex(),
                                variableTable.getTempObjectIndex(),
                                frame),
                        invokePushMethodState(
                                id,
                                variableTable.getContinuationIndex(),
                                variableTable.getOperandStackArrayIndex(),
                                variableTable.getLocalVarTableArrayIndex(),
                                variableTable.getTempObjectIndex()),
                        yield ? returnDummy(methodReturnType) : empty(),
                        addLabel(restoreLabelNode)
                        
                );
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

    public InsnList getRestoreInsnList() {
        return restoreInsnList;
    }

    public InsnList getStoreInsnList() {
        return storeInsnList;
    }

}
