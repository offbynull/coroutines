package com.offbynull.coroutines.instrumenter;

import static com.offbynull.coroutines.instrumenter.InstructionUtils.addLabel;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.call;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.cloneInvokeNode;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.construct;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.debugPrint;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.ifIntegersEqual;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.jumpTo;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.loadIntConst;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.loadLocalVariableTable;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.loadVar;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.loadOperandStack;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.merge;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.pop;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.returnDummy;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.saveLocalVariableTable;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.saveVar;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.saveOperandStack;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.tableSwitch;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.throwException;
import static com.offbynull.coroutines.instrumenter.SearchUtils.findInvocationsOf;
import static com.offbynull.coroutines.instrumenter.SearchUtils.findInvocationsThatStartWithParameters;
import static com.offbynull.coroutines.instrumenter.SearchUtils.findMethodsThatStartWithParameters;
import static com.offbynull.coroutines.instrumenter.SearchUtils.searchForOpcodes;
import com.offbynull.coroutines.instrumenter.VariableTable.Variable;
import com.offbynull.coroutines.user.Continuation;
import static com.offbynull.coroutines.user.Continuation.MODE_NORMAL;
import static com.offbynull.coroutines.user.Continuation.MODE_SAVING;
import com.offbynull.coroutines.user.Continuation.MethodState;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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

public final class Instrumenter {

    private static final Type CONTINUATION_CLASS_TYPE = Type.getType(Continuation.class);
    private static final Type CONTINUATION_SUSPEND_METHOD_TYPE
            = Type.getType(MethodUtils.getAccessibleMethod(Continuation.class, "suspend"));
    private static final Method CONTINUATION_GETMODE_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "getMode");
    private static final Method CONTINUATION_SETMODE_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "setMode", Integer.TYPE);
    private static final Constructor<MethodState> METHODSTATE_INIT_METHOD
            = ConstructorUtils.getAccessibleConstructor(MethodState.class, Integer.TYPE, Object[].class, Object[].class);
    private static final Method CONTINUATION_INSERTLAST_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "insertLast", MethodState.class);
    private static final Method CONTINUATION_REMOVEFIRST_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "removeFirst");
    private static final Method METHODSTATE_GETCONTINUATIONPOINT_METHOD
            = MethodUtils.getAccessibleMethod(MethodState.class, "getContinuationPoint");
    private static final Method METHODSTATE_GETLOCALTABLE_METHOD
            = MethodUtils.getAccessibleMethod(MethodState.class, "getLocalTable");
    private static final Method METHODSTATE_GETSTACK_METHOD
            = MethodUtils.getAccessibleMethod(MethodState.class, "getStack");
    
    private Map<String, String> superClassMapping;
    
    public Instrumenter(List<File> classPaths) throws IOException {
        Validate.notNull(classPaths);
        Validate.noNullElements(classPaths);
        
        superClassMapping = SearchUtils.getSuperClassMappings(classPaths);
    }

    public byte[] instrument(byte[] input) {
        try {
            return instrumentClass(input);
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe); // this should never happen
        }
    }
    
    private byte[] instrumentClass(byte[] input) throws IOException {
        Validate.notNull(input);
        Validate.isTrue(input.length > 0);
        
        
        ByteArrayInputStream bais = new ByteArrayInputStream(input);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        

        // Read class as tree model
        ClassReader cr = new ClassReader(bais);
        ClassNode classNode = new ClassNode();
        cr.accept(classNode, 0);
        
        // Don't do anything if interface
        if ((classNode.access & Opcodes.ACC_INTERFACE) == Opcodes.ACC_INTERFACE) {
            return input.clone();
        }

        // Find methods that need to be instrumented
        List<MethodNode> methodNodesToInstrument
                = findMethodsThatStartWithParameters(classNode.methods, CONTINUATION_CLASS_TYPE);

        for (MethodNode methodNode : methodNodesToInstrument) {
            // Check if method is constructor
            Validate.isTrue(!"<init>".equals(methodNode.name), "Instrumentation of constructors not allowed");
            
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
                throw new IllegalArgumentException("Analyzer failed", ae);
            }

            // Find invocations of continuation points
            List<AbstractInsnNode> suspendInvocationInsnNodes
                    = findInvocationsOf(methodNode.instructions, CONTINUATION_SUSPEND_METHOD_TYPE);
            List<AbstractInsnNode> saveInvocationInsnNodes
                    = findInvocationsThatStartWithParameters(methodNode.instructions, CONTINUATION_CLASS_TYPE);

            // Generate local variable indices
            boolean isStatic = (methodNode.access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC;
            VariableTable varTable = new VariableTable(classNode, methodNode);

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

            // Manage new variables and arguments
            int continuationArgIdx = isStatic ? 0 : 1;
            Variable contArg = varTable.getArgument(continuationArgIdx);
            Variable methodStateVar = varTable.acquireExtra(Type.getType(MethodState.class));
            Variable savedLocalsVar = varTable.acquireExtra(Type.getType(Object[].class));
            Variable savedStackVar = varTable.acquireExtra(Type.getType(Object[].class));
            Variable tempObjVar = varTable.acquireExtra(Type.getType(Object.class));
            
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
            LabelNode startOfMethodLabelNode = new LabelNode();
            InsnList entryPointInsnList
                    = merge(
                            tableSwitch(
                                    call(CONTINUATION_GETMODE_METHOD, loadVar(contArg)),
                                    throwException("Unrecognized state"),
                                    0,
                                    jumpTo(startOfMethodLabelNode),
                                    throwException("Unexpected state (saving not allowed at this point)"),
                                    merge(
                                            call(CONTINUATION_REMOVEFIRST_METHOD, loadVar(contArg)),
                                            saveVar(methodStateVar),
                                            call(METHODSTATE_GETLOCALTABLE_METHOD, loadVar(methodStateVar)),
                                            saveVar(savedLocalsVar),
                                            call(METHODSTATE_GETSTACK_METHOD, loadVar(methodStateVar)),
                                            saveVar(savedStackVar),
                                            tableSwitch(
                                                    call(METHODSTATE_GETCONTINUATIONPOINT_METHOD, loadVar(methodStateVar)),
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
            //          continuation.setMode(MODE_SAVING);
            //          return <dummy>;
            //          restorePoint_<number>:
            //          continuation.setMode(MODE_NORMAL);
            //      #ENDIF
            //
            //      #IFDEF !suspend
            //          restorePoint_<number>:
            //          <method invocation>
            //          if (continuation.getMode() == MODE_SAVING) {
            //              return <dummy>;
            //          }
            //      #ENDIF
            continuationPoints.forEach((cp) -> {
                InsnList saveBeforeInvokeInsnList
                        = merge(
//                                debugPrint("saving operand stack"),
                                saveOperandStack(savedStackVar, tempObjVar, cp.getFrame()),
//                                debugPrint("saving locals"),
                                saveLocalVariableTable(savedLocalsVar, tempObjVar, cp.getFrame()),
//                                debugPrint("calling insert"),
                                call(CONTINUATION_INSERTLAST_METHOD, loadVar(contArg),
                                        construct(METHODSTATE_INIT_METHOD,
                                                loadIntConst(cp.getId()),
                                                loadVar(savedStackVar),
                                                loadVar(savedLocalsVar)))
                        );
                
                InsnList insnList;
                if (cp.isSuspend()) {
                    // When Continuation.suspend() is called, it's a termination point. We want to ...
                    //
                    //    1. Save our stack, locals, and restore point and push them on to the Continuation object.
                    //    2. Put the continuation in to SAVING mode.
                    //    3. Remove the call to suspend() and return a dummy value in its place.
                    //    4. Add a label just after the return we added (used by loading code when execution is continued).
                    //
                    // By going in to SAVING mode and returning, callers up the chain will know to stop their flow of execution and return
                    // immediately (see else block below)
                    insnList
                            = merge(
                                    saveBeforeInvokeInsnList,                           // save
                                                                                        // set saving mode
//                                    debugPrint("setting mode to saving"),
                                    call(CONTINUATION_SETMODE_METHOD, loadVar(contArg), loadIntConst(MODE_SAVING)),
//                                    debugPrint("returning dummy value"),
                                    returnDummy(returnType),                            // return dummy value
                                    addLabel(cp.getRestoreLabelNode()),                 // add restore point for when in loading mode
//                                    debugPrint("entering restore point"),
                                    pop(), // frame at the time of invocation to Continuation.suspend() has Continuation reference on the
                                           // stack that would have been consumed by that invocation... since we're removing that call, we
                                           // also need to pop the Continuation reference from the stack... it's important that we
                                           // explicitly do it at this point becuase during loading the stack will be restored with a point
                                           // to that continuation object
//                                    debugPrint("going back in to normal mode"),
                                                                                        // we're back in to a loading state now
                                    call(CONTINUATION_SETMODE_METHOD, loadVar(contArg), loadIntConst(MODE_NORMAL))
                                    
                            );
                } else {
                    // When a method that takes in a Continuation object as a parameter is called, We want to ...
                    //
                    //    1. Save our stack, locals, and restore point and push them on to the Continuation object.
                    //    2. Add a label just before the call (used by the loading code when execution is continued).
                    //    3. Call the method as it normally would be.
                    //    4. Once the method returns, we check to see if the method is in SAVING mode and return immediately if it is.
                    //
                    // If in SAVING mode, it means that suspend() was invoked somewhere down the chain. Callers above it must stop their
                    // normal flow of execution and return immediately.
                    insnList
                            = merge(
                                    saveBeforeInvokeInsnList,                   // save
                                    addLabel(cp.getRestoreLabelNode()),         // add restore point for when in loading mode
//                                    debugPrint("invoking"),
                                    cloneInvokeNode(cp.getInvokeInsnNode()),    // invoke method
//                                    debugPrint("testing if in saving mode"),
                                    ifIntegersEqual(// if we're saving after invoke, return dummy value
                                            call(CONTINUATION_GETMODE_METHOD, loadVar(contArg)),
                                            loadIntConst(MODE_SAVING),
                                            returnDummy(returnType)
                                    )
                            );
                }
                
                methodNode.instructions.insertBefore(cp.getInvokeInsnNode(), insnList);
                methodNode.instructions.remove(cp.getInvokeInsnNode());
            });
        }

        // Write tree model back out as class
        ClassWriter cw = new CustomClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES, superClassMapping);
        classNode.accept(cw);

        baos.write(cw.toByteArray());
        
        
        return baos.toByteArray();
    }
}
