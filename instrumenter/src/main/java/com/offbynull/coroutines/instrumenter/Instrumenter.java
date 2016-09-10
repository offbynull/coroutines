/*
 * Copyright (c) 2016, Kasra Faghihi, All rights reserved.
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

import com.offbynull.coroutines.instrumenter.asm.ClassInformationRepository;
import com.offbynull.coroutines.instrumenter.asm.FileSystemClassInformationRepository;
import com.offbynull.coroutines.instrumenter.asm.SimpleClassWriter;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.findMethodsWithParameter;
import com.offbynull.coroutines.instrumenter.asm.SimpleClassNode;
import com.offbynull.coroutines.instrumenter.generators.DebugGenerators.MarkerType;
import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.Instrumented;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Instruments methods in Java classes that are intended to be run as coroutines. Tested with Java 1.2 and Java 8, so hopefully thing should
 * work with all versions of Java inbetween.
 * @author Kasra Faghihi
 */
public final class Instrumenter {

    private static final Type INSTRUMENTED_CLASS_TYPE = Type.getType(Instrumented.class);
    private static final Type CONTINUATION_CLASS_TYPE = Type.getType(Continuation.class);

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

        classRepo = FileSystemClassInformationRepository.create(classpath);
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
     * @param debugMarkerType type of debugging marker to inject in to the instrumented code
     * @return instrumented class
     * @throws IllegalArgumentException if the class could not be instrumented for some reason
     * @throws NullPointerException if any argument is {@code null}
     */
    public byte[] instrument(byte[] input, MarkerType debugMarkerType) {
        Validate.notNull(input);
        Validate.notNull(debugMarkerType);
        Validate.isTrue(input.length > 0);
        
        // Read class as tree model -- because we're using SimpleClassNode, JSR blocks get inlined
        ClassReader cr = new ClassReader(input);
        ClassNode classNode = new SimpleClassNode();
        cr.accept(classNode, 0);

        // Is this class an interface? if so, skip it
        if ((classNode.access & Opcodes.ACC_INTERFACE) == Opcodes.ACC_INTERFACE) {
            return input.clone();
        }

        // Has this class already been instrumented? if so, skip it
        if (classNode.interfaces.contains(INSTRUMENTED_CLASS_TYPE.getInternalName())) {
            return input.clone();
        }

        // Find methods that need to be instrumented. If none are found, skip
        List<MethodNode> methodNodesToInstrument = findMethodsWithParameter(classNode.methods, CONTINUATION_CLASS_TYPE);
        if (methodNodesToInstrument.isEmpty()) {
            return input.clone();
        }
        
        // Add the "Instrumented" interface to this class so if we ever come back to it, we can skip it
        classNode.interfaces.add(INSTRUMENTED_CLASS_TYPE.getInternalName());

        // Instrument each method that needs to be instrumented
        MethodAnalyzer analyzer = new MethodAnalyzer(classRepo);
        MethodInstrumenter instrumenter = new MethodInstrumenter();
        for (MethodNode methodNode : methodNodesToInstrument) {
            MethodProperties methodProps = analyzer.analyze(classNode, methodNode, debugMarkerType);
            
            // If methodProps is null, it means that the analyzer determined that the method doesn't need to be instrumented.
            if (methodProps != null) {
                instrumenter.instrument(methodNode, methodProps);
            }
        }

        // Write tree model back out as class
        ClassWriter cw = new SimpleClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES, classRepo);
        classNode.accept(cw);
        return cw.toByteArray();
    }
}
