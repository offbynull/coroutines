package com.offbynull.coroutines.instrumenter;

import com.offbynull.coroutines.user.Continuation;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
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
        List<MethodNode> methodNodesToInstrument = 
                SearchUtils.findMethodsThatStartWithParameters(classNode.methods, CONTINUATION_CLASS_TYPE);

        
        
        for (MethodNode methodNode : methodNodesToInstrument) {
            // Analyze method
            Frame<BasicValue>[] frames;
            try {
                frames = new Analyzer<>(new SimpleVerifier()).analyze(classNode.name, methodNode);
            } catch (AnalyzerException ae) {
                throw new IllegalArgumentException("Anaylzer failed", ae);
            }
            
            // Find invocations of yield and other continuation methods
            List<AbstractInsnNode> yieldInvocations =
                    SearchUtils.findInvocationsOf(methodNode.instructions, CONTINUATION_YIELD_METHOD_TYPE);
            List<AbstractInsnNode> saveInvocations =
                    SearchUtils.findInvocationsThatStartWithParameters(methodNode.instructions, CONTINUATION_CLASS_TYPE);
        }
        
        
        
        // Write tree model back out as class
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(cw);
    }
}
