package com.offbynull.coroutines.instrumenter;

import static com.offbynull.coroutines.instrumenter.InstructionUtils.addLabel;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.call;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.construct;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.empty;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.invokePopMethodState;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.jumpTo;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.loadIntConst;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.loadLocalVariableTable;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.loadObjectVar;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.loadOperandStack;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.merge;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.returnDummy;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.saveLocalVariableTable;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.saveObjectVar;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.saveOperandStack;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.tableSwitch;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.throwException;
import static com.offbynull.coroutines.instrumenter.SearchUtils.findInvocationsOf;
import static com.offbynull.coroutines.instrumenter.SearchUtils.findInvocationsThatStartWithParameters;
import static com.offbynull.coroutines.instrumenter.SearchUtils.findMethodsThatStartWithParameters;
import static com.offbynull.coroutines.instrumenter.SearchUtils.searchForOpcodes;
import com.offbynull.coroutines.instrumenter.VariableTable.Variable;
import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.Continuation.MethodState;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
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
import org.objectweb.asm.util.TraceClassVisitor;

public final class Instrumenter {

    private static final Type CONTINUATION_CLASS_TYPE = Type.getType(Continuation.class);
    private static final Type CONTINUATION_SUSPEND_METHOD_TYPE = Type.getType(
            MethodUtils.getAccessibleMethod(Continuation.class, "suspend"));
    private static final Method CONTINUATION_GETMODE_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "getMode");
    private static final Constructor<MethodState> METHODSTATE_INIT_METHOD
            = ConstructorUtils.getAccessibleConstructor(MethodState.class, Integer.TYPE, Object[].class, Object[].class);
    private static final Method CONTINUATION_PUSH_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "push", MethodState.class);
    private static final Method CONTINUATION_POP_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "pop");
    private static final Method METHODSTATE_GETCONTINUATIONPOINT_METHOD
            = MethodUtils.getAccessibleMethod(MethodState.class, "getContinuationPoint");
    private static final Method METHODSTATE_GETLOCALTABLE_METHOD
            = MethodUtils.getAccessibleMethod(MethodState.class, "getLocalTable");
    private static final Method METHODSTATE_GETSTACK_METHOD
            = MethodUtils.getAccessibleMethod(MethodState.class, "getStack");
    

    public byte[] instrument(byte[] input) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(input);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        instrument(bais, baos);
        return baos.toByteArray();
    }

    private void instrument(InputStream inputStream, OutputStream outputStream) throws IOException {
        // Read class as tree model
        ClassReader cr = new ClassReader(inputStream);
        ClassNode classNode = new ClassNode();
        cr.accept(classNode, 0);

        // Find methods that need to be instrumented
        List<MethodNode> methodNodesToInstrument
                = findMethodsThatStartWithParameters(classNode.methods, CONTINUATION_CLASS_TYPE);

        for (MethodNode methodNode : methodNodesToInstrument) {
            // Check method does not contain invalid bytecode
            Validate.isTrue(searchForOpcodes(methodNode.instructions, Opcodes.JSR, Opcodes.MONITORENTER, Opcodes.MONITOREXIT).isEmpty(),
                    "JSR/MONITORENTER/MONITOREXIT are not allowed");

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
            List<AbstractInsnNode> suspendInvocationInsnNodes
                    = findInvocationsOf(methodNode.instructions, CONTINUATION_SUSPEND_METHOD_TYPE);
            List<AbstractInsnNode> saveInvocationInsnNodes
                    = findInvocationsThatStartWithParameters(methodNode.instructions, CONTINUATION_CLASS_TYPE);

            // Generate local variable indices
            VariableTable varTable = new VariableTable(Type.getMethodType(methodNode.desc), methodNode.maxLocals);

            // Generate instructions for continuation points
            int nextId = 0;
            List<ContinuationPoint> continuationPoints = new LinkedList<>();

            for (AbstractInsnNode suspendInvocationInsnNode : suspendInvocationInsnNodes) {
                int insnIdx = methodNode.instructions.indexOf(suspendInvocationInsnNode);
                ContinuationPoint cp = new ContinuationPoint(true, nextId, suspendInvocationInsnNode, frames[insnIdx], returnType);
                continuationPoints.add(cp);
                nextId++;
            }

            for (AbstractInsnNode saveInvocationInsnNode : saveInvocationInsnNodes) {
                int insnIdx = methodNode.instructions.indexOf(saveInvocationInsnNode);
                ContinuationPoint cp = new ContinuationPoint(false, nextId, saveInvocationInsnNode, frames[insnIdx], returnType);
                continuationPoints.add(cp);
                nextId++;
            }

            // Generate entrypoint instructions...
            //
            //    switch(continuation.getMode()) {
            //        case NORMAL: goto start
            //        case SAVING: throw exception
            //        case LOADING:
            //        {
            //            MethodState methodState = continuation.pop();
            //            Object[] stack = methodState.getStack();
            //            Object[] localVars = methodState.getLocalTable();
            //            switch(methodState.getContinuationPoint()) {
            //                case <number>:
            //                    restoreOperandStack(stack);
            //                    restoreLocalsStack(localVars);
            //                    goto restorePoint_<number>;
            //                ...
            //                ...
            //                ...
            //                default: throw exception
            //            }
            //            goto start;
            //        }
            //        default: throw exception
            //    }
            //
            //    start:
            //        ...
            //        ...
            //        ...
            int continuationArgIdx = (methodNode.access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC ? 0 : 1;
            
            Variable contArg = varTable.getArgument(continuationArgIdx);
            Variable methodStateVar = varTable.acquireExtra(Type.getType(MethodState.class));
            Variable savedLocalsVar = varTable.acquireExtra(Type.getType(Object[].class));
            Variable savedStackVar = varTable.acquireExtra(Type.getType(Object[].class));
            Variable tempObjVar = varTable.acquireExtra(Type.getType(Object.class));
            
            
            
            LabelNode startOfMethodLabelNode = new LabelNode();
            InsnList entryPointInsnList
                    = merge(
                            tableSwitch(
                                    call(CONTINUATION_GETMODE_METHOD, loadObjectVar(contArg)),
                                    throwException("Unrecognized state"),
                                    0,
                                    jumpTo(startOfMethodLabelNode),
                                    throwException("Unexpected state (saving not allowed at this point)"),
                                    merge(
                                            call(CONTINUATION_POP_METHOD, loadObjectVar(contArg)),
                                            saveObjectVar(methodStateVar),
                                            call(METHODSTATE_GETLOCALTABLE_METHOD, loadObjectVar(methodStateVar)),
                                            saveObjectVar(savedLocalsVar),
                                            call(METHODSTATE_GETSTACK_METHOD, loadObjectVar(methodStateVar)),
                                            saveObjectVar(savedStackVar),
                                            tableSwitch(
                                                    call(METHODSTATE_GETCONTINUATIONPOINT_METHOD, loadObjectVar(methodStateVar)),
                                                    throwException("Unrecognized restore id"),
                                                    0,
                                                    continuationPoints.stream().map((cp) -> {
                                                        InsnList ret
                                                                = merge(
                                                                        loadOperandStack(savedStackVar, tempObjVar, cp.getFrame()),
                                                                        loadLocalVariableTable(savedLocalsVar, tempObjVar, cp.getFrame()),
                                                                        jumpTo(cp.getRestoreLabelNode())
                                                                );
                                                        return ret;
                                                    }).toArray((x) -> new InsnList[x])
                                            )
                                            // jump to not required here, switch above either throws exception or jumps to restore point
                                    )
                            ),
                            addLabel(startOfMethodLabelNode)
                    );
            methodNode.instructions.insert(entryPointInsnList);

            // Add store logic and restore addLabel for each continuation point
            //
            //      Object[] stack = saveOperandStack();
            //      Object[] locals = saveLocalsStackHere();
            //      continuation.push(new MethodState(<number>, stack, locals);
            //      #IFDEF suspend
            //          return <value>;       
            //      #ENDIF
            //
            //      restorePoint_<number>:
            //      #IFDEF !suspend
            //          <method invocation>
            //      #ENDIF
            continuationPoints.forEach((cp) -> {
                InsnList savePointInsnList
                        = merge(
                                saveOperandStack(savedStackVar, tempObjVar, cp.getFrame()),
                                saveLocalVariableTable(savedLocalsVar, tempObjVar, cp.getFrame()),
                                call(CONTINUATION_PUSH_METHOD, loadObjectVar(contArg),
                                        construct(METHODSTATE_INIT_METHOD,
                                                loadIntConst(cp.getId()),
                                                loadObjectVar(savedStackVar),
                                                loadObjectVar(savedLocalsVar))),
                                cp.isSuspend() ? returnDummy(returnType) : empty(),
                                addLabel(cp.getRestoreLabelNode())
                        );
                
                methodNode.instructions.insertBefore(cp.getInvokeInsnNode(), savePointInsnList);                
                if (cp.isSuspend()) {
                    methodNode.instructions.remove(cp.getInvokeInsnNode());
                }
            });
        }

        classNode.accept(new TraceClassVisitor(new PrintWriter(System.out)));
        // Write tree model back out as class
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(cw);

        outputStream.write(cw.toByteArray());
    }
}
