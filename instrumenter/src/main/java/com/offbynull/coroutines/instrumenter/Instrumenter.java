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
import static com.offbynull.coroutines.instrumenter.InstructionUtils.tableSwitch;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.throwException;
import com.offbynull.coroutines.user.Continuation;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SimpleVerifier;

public final class Instrumenter {

    private static final Type CONTINUATION_CLASS_TYPE = Type.getType(Continuation.class);
    private static final Type CONTINUATION_YIELD_METHOD_TYPE = Type.getType(MethodUtils.getAccessibleMethod(Continuation.class, "yield"));

    public void instrument(InputStream classInputStream, OutputStream classOutputStream) throws IOException {
        // Read class as tree model
        ClassReader cr = new ClassReader(classInputStream);
        ClassNode classNode = new ClassNode();
        cr.accept(classNode, 0);

        // Find methods that need to be instrumented
        List<MethodNode> methodNodesToInstrument
                = SearchUtils.findMethodsThatStartWithParameters(classNode.methods, CONTINUATION_CLASS_TYPE);

        for (MethodNode methodNode : methodNodesToInstrument) {
            // Get return type
            Type returnType = Type.getMethodType(methodNode.desc).getReturnType();

            // Analyze method
            Frame<BasicValue>[] frames;
            try {
                frames = new Analyzer<>(new SimpleVerifier()).analyze(classNode.name, methodNode);
            } catch (AnalyzerException ae) {
                throw new IllegalArgumentException("Anaylzer failed", ae);
            }

            // Find invocations of continuation points
            List<AbstractInsnNode> yieldInvocationInsnNodes
                    = SearchUtils.findInvocationsOf(methodNode.instructions, CONTINUATION_YIELD_METHOD_TYPE);
            List<AbstractInsnNode> saveInvocationInsnNodes
                    = SearchUtils.findInvocationsThatStartWithParameters(methodNode.instructions, CONTINUATION_CLASS_TYPE);

            // Generate local variable indices
            VariableTable variableTable = new VariableTable(methodNode.access, methodNode.maxLocals);

            // Generate instructions for continuation points
            int nextId = 0;
            List<ContinuationPoint> continuationPoints = new LinkedList<>();

            for (AbstractInsnNode yieldInvocationInsnNode : yieldInvocationInsnNodes) {
                int insnIdx = methodNode.instructions.indexOf(yieldInvocationInsnNode);
                ContinuationPoint cp = new ContinuationPoint(true, nextId, yieldInvocationInsnNode, frames[insnIdx], variableTable, returnType);
                continuationPoints.add(cp);
                nextId++;
            }

            for (AbstractInsnNode saveInvocationInsnNode : saveInvocationInsnNodes) {
                int insnIdx = methodNode.instructions.indexOf(saveInvocationInsnNode);
                ContinuationPoint cp = new ContinuationPoint(false, nextId, saveInvocationInsnNode, frames[insnIdx], variableTable, returnType);
                continuationPoints.add(cp);
                nextId++;
            }

            // Generate entrypoint instructions...
            //
            //    switch(mode) {
            //        case LOADING:
            //        {
            //            MethodState methodState = continuation.pop();
            //            switch(methodState.getContinuationPoint()) {
            //                case <number>:
            //                    restoreOperandStack(methodState.getOperandStack());
            //                    restoreLocalsStack(methodState.getLocals());
            //                    goto restorePoint_<number>;
            //                ...
            //                ...
            //                ...
            //                default: throw exception
            //            }
            //            goto start;
            //        }
            //        case NORMAL: goto start
            //        case SAVING: throw exception
            //        default: throw exception
            //    }
            //
            //    start:
            //        ...
            //        ...
            //        ...
            LabelNode startOfMethodLabelNode = new LabelNode();
            InsnList entryPointInsnList
                    = merge(tableSwitch(
                                    throwException("Unrecognized state"),
                                    throwException("Unexpected state (saving not allowed at this point)"),
                                    tableSwitch(
                                            throwException("Unrecognized restore id"),
                                            continuationPoints.stream().map(
                                                    (x) -> generateLoadPoint(x, variableTable)).toArray((x) -> new InsnList[x])
                                    ),
                                    jumpTo(startOfMethodLabelNode)
                            ),
                            addLabel(startOfMethodLabelNode)
                    );
            methodNode.instructions.insert(entryPointInsnList);

            // Add store logic and restore addLabel for each continuation point
            //
            //      Object[] stack = saveOperandStack();
            //      Object[] locals = saveLocalsStackHere();
            //      continuation.push(new MethodState(<number>, stack, locals);
            //      #IFDEF yield
            //          return <value>;       
            //      #ENDIF
            //
            //      restorePoint_<number>:
            //      #IFDEF !yield
            //          <method invocation>
            //      #ENDIF
            continuationPoints.forEach((x) -> {
                methodNode.instructions.insertBefore(x.getInvokeInsnNode(), generateSavePoint(returnType, x, variableTable));
                if (x.isYield()) {
                    methodNode.instructions.remove(x.getInvokeInsnNode());
                }
            });
        }

        // Write tree model back out as class
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(cw);
    }

    private InsnList generateSavePoint(Type methodReturnType, ContinuationPoint continuationPoint, VariableTable variableTable) {
        return merge(
                saveOperandStack(
                        variableTable.getOperandStackArrayIndex(),
                        variableTable.getTempObjectIndex(),
                        continuationPoint.getFrame()),
                saveLocalVariableTable(
                        variableTable.getLocalVarTableArrayIndex(),
                        variableTable.getTempObjectIndex(),
                        continuationPoint.getFrame()),
                invokePushMethodState(
                        continuationPoint.getId(),
                        variableTable.getContinuationIndex(),
                        variableTable.getOperandStackArrayIndex(),
                        variableTable.getLocalVarTableArrayIndex(),
                        variableTable.getTempObjectIndex()),
                continuationPoint.isYield() ? returnDummy(methodReturnType) : empty(),
                addLabel(continuationPoint.getRestoreLabelNode())
        );
    }

    private InsnList generateLoadPoint(ContinuationPoint continuationPoint, VariableTable variableTable) {
        return merge(
                invokePopMethodState(
                        variableTable.getContinuationIndex(),
                        variableTable.getOperandStackArrayIndex(),
                        variableTable.getLocalVarTableArrayIndex(),
                        variableTable.getTempObjectIndex()),
                loadOperandStack(
                        variableTable.getOperandStackArrayIndex(),
                        variableTable.getTempObjectIndex(),
                        continuationPoint.getFrame()),
                loadLocalVariableTable(
                        variableTable.getLocalVarTableArrayIndex(),
                        variableTable.getTempObjectIndex(),
                        continuationPoint.getFrame()),
                jumpTo(continuationPoint.getRestoreLabelNode())
        );
    }
}
