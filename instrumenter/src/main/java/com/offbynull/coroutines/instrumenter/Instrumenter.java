/*
 * Copyright (c) 2018, Kasra Faghihi, All rights reserved.
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

import com.offbynull.coroutines.instrumenter.InstrumentationState.ControlFlag;
import com.offbynull.coroutines.instrumenter.asm.ClassInformationRepository;
import com.offbynull.coroutines.instrumenter.asm.ClassResourceClassInformationRepository;
import com.offbynull.coroutines.instrumenter.asm.CompositeClassInformationRepository;
import com.offbynull.coroutines.instrumenter.asm.FileSystemClassInformationRepository;
import com.offbynull.coroutines.instrumenter.asm.SimpleClassWriter;
import com.offbynull.coroutines.instrumenter.asm.SimpleClassNode;
import com.offbynull.coroutines.instrumenter.asm.SimpleVerifier;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

/**
 * Instruments methods in Java classes that are intended to be run as coroutines. Tested with Java 1.4 and Java 8, so hopefully thing should
 * work with all versions of Java inbetween.
 * @author Kasra Faghihi
 */
public final class Instrumenter {

    private ClassInformationRepository classRepo;

    /**
     * Constructs a {@link Instrumenter} object from a filesystem classpath (folders and JARs).
     * @param classpath classpath JARs and folders to use for instrumentation (this is needed by ASM to generate stack map frames).
     * @throws IOException if classes in the classpath could not be loaded up
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     */
    public Instrumenter(List<File> classpath) throws IOException {
        Validate.notNull(classpath);
        Validate.noNullElements(classpath);

        classRepo = new CompositeClassInformationRepository(
                new ClassResourceClassInformationRepository(Instrumenter.class.getClassLoader()), // access to core JRE classes
                FileSystemClassInformationRepository.create(classpath)                            // access to user classes
        );
    }

    /**
     * Constructs a {@link Instrumenter} object.
     * @param repo class information repository (this is needed by ASM to generate stack map frames).
     * @throws NullPointerException if any argument is {@code null}
     */
    public Instrumenter(ClassInformationRepository repo) {
        Validate.notNull(repo);

        classRepo = repo;
    }

    /**
     * Instruments a class.
     * @param input class file contents
     * @param settings instrumentation settings
     * @return instrumentation results
     * @throws IllegalArgumentException if the class could not be instrumented for some reason
     * @throws NullPointerException if any argument is {@code null}
     */
    public InstrumentationResult instrument(byte[] input, InstrumentationSettings settings) {
        Validate.notNull(input);
        Validate.notNull(settings);
        Validate.isTrue(input.length > 0);



        // Read class as tree model -- because we're using SimpleClassNode, JSR blocks get inlined
        ClassReader cr = new ClassReader(input);
        ClassNode classNode = new SimpleClassNode();
        cr.accept(classNode, 0);
        
        
        
        // Recompute stackmap frames.
        classNode = reconstructStackMapFrames(classNode);



        // Apply passes.
        InstrumentationPass[] passes = new InstrumentationPass[] {
            new IdentifyInstrumentationPass(),          // identify methods for instrumentation
            new AnalyzeInstrumentationPass(),           // analyze methods for instrumentation
            new SerializationPreInstrumentationPass(),  // create .coroutinesinfo files for methods to be instrumented
            new PerformInstrumentationPass(),           // perform instrumentation of methods
            new SerializationPostInstrumentationPass(), // add fields needed for serializer/deserializer to identify versioning info
            new AutoSerializableInstrumentationPass()   // make class serializable + give serializationuid
        };
        InstrumentationState passState = new InstrumentationState(settings, classRepo);

        for (InstrumentationPass pass : passes) {
            pass.pass(classNode, passState);

            ControlFlag controlFlag = passState.control();
            switch (controlFlag) {
                case CONTINUE_INSTRUMENT:
                    break;
                case NO_INSTRUMENT:
                    return new InstrumentationResult(input); // class should not be instrumented -- return original data.
                default:
                    throw new IllegalStateException(); // should never happen
            }
        }



        // Write tree model back out as class -- NOTE: If we get a NegativeArraySizeException on classNode.accept(), it likely means that
        //                                             we're doing bad things with the stack. So, before writing the class out and returning
        //                                             it, we call verifyClassIntegrity() to check and make sure everything is okay.
        // RE-ENABLE ONLY IF JVM COMPLAINS ABOUT INSTRUMENTED CLASSES AND YOU NEED TO DEBUG, KEEP COMMENTED OUT FOR PRODUCTION
        // verifyClassIntegrity(classNode);

        ClassWriter cw = new SimpleClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES, classRepo);
        classNode.accept(cw);
        
        byte[] classData = cw.toByteArray();
        Map<String, byte[]> extraFiles = passState.extraFiles();

        return new InstrumentationResult(classData, extraFiles);
    }


    private void verifyClassIntegrity(ClassNode classNode) {
        // Do not COMPUTE_FRAMES. If you COMPUTE_FRAMES and you pop too many items off the stack or do other weird things that mess up the
        // stack map frames, it'll crash on classNode.accept(cw).
        ClassWriter cw = new SimpleClassWriter(ClassWriter.COMPUTE_MAXS/* | ClassWriter.COMPUTE_FRAMES*/, classRepo);
        classNode.accept(cw);
        
        byte[] classData = cw.toByteArray();

        ClassReader cr = new ClassReader(classData);
        classNode = new SimpleClassNode();
        cr.accept(classNode, 0);

        for (MethodNode methodNode : classNode.methods) {
            Analyzer<BasicValue> analyzer = new Analyzer<>(new SimpleVerifier(classRepo));
            try {
                analyzer.analyze(classNode.name, methodNode);
            } catch (AnalyzerException e) {
                // IF WE DID OUR INSTRUMENTATION RIGHT, WE SHOULD NEVER GET AN EXCEPTION HERE!!!!
                StringWriter writer = new StringWriter();
                PrintWriter printWriter = new PrintWriter(writer);
                
                printWriter.append(methodNode.name + " encountered " + e);
                
                Printer printer = new Textifier();
                TraceMethodVisitor traceMethodVisitor = new TraceMethodVisitor(printer);
                
                AbstractInsnNode insn = methodNode.instructions.getFirst();
                while (insn != null) {
                    if (insn == e.node) {
                        printer.getText().add("----------------- BAD INSTRUCTION HERE -----------------\n");
                    }
                    insn.accept(traceMethodVisitor);
                    insn = insn.getNext();
                }
                printer.print(printWriter);
                printWriter.flush(); // we need this or we'll get incomplete results
                
                throw new IllegalStateException(writer.toString(), e);
            }
        }
    }
    
    private ClassNode reconstructStackMapFrames(ClassNode classNode) {
        // Remove stackmap frames from method
        for (MethodNode methodNode : classNode.methods) {
            if (methodNode.instructions == null) {
                continue;
            }
            
            AbstractInsnNode insn = methodNode.instructions.getFirst();
            while (insn != null) {
                AbstractInsnNode nextInsn = insn.getNext();
                if (insn.getType() == AbstractInsnNode.FRAME) {
                    methodNode.instructions.remove(insn);
                }
                insn = nextInsn;
            }
        }
        
        // Write out the class (recomputes stackmap frames)
        ClassWriter cw = new SimpleClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES, classRepo);
        classNode.accept(cw);
        
        byte[] temp = cw.toByteArray();
        
        // Read the class back in
        ClassReader cr = new ClassReader(temp);
        ClassNode newClassNode = new SimpleClassNode();
        cr.accept(newClassNode, 0);
        
        // Return the class with newly computed stackmap frames
        return newClassNode;
    }
}
