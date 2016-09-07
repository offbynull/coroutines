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
package com.offbynull.coroutines.instrumenter.asm;

import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.addLabel;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.call;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.construct;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.jumpTo;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.loadVar;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.merge;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.saveVar;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.tableSwitch;
import com.offbynull.coroutines.instrumenter.testhelpers.TestUtils;
import static com.offbynull.coroutines.instrumenter.testhelpers.TestUtils.readZipFromResource;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.pop;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.returnVoid;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.throwRuntimeException;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.pop;

public class SimpleVerifierTest {

    private FileSystemClassInformationRepository classRepo;
    private ClassNode classNode;
    private MethodNode methodNode;

    @Before
    public void setUp() throws IOException {
        classRepo = new FileSystemClassInformationRepository();
        classRepo.addClasspath(TestUtils.getClasspath());

        byte[] classData = readZipFromResource("SimpleStub.zip").get("SimpleStub.class");

        ClassReader classReader = new ClassReader(classData);
        classNode = new ClassNode();
        classReader.accept(classNode, 0);

        methodNode = classNode.methods.get(1); // stub should be here
    }

    @Test
    public void testVertificationWithoutUsingClassLoader() throws Exception {

        // augment method to take in a single int argument
        methodNode.desc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.INT_TYPE);

        // augment method instructions
        VariableTable varTable = new VariableTable(classNode, methodNode);

        Class<?> iterableClass = Iterable.class;
        Constructor arrayListConstructor = ConstructorUtils.getAccessibleConstructor(ArrayList.class);
        Constructor linkedListConstructor = ConstructorUtils.getAccessibleConstructor(LinkedList.class);
        Constructor hashSetConstructor = ConstructorUtils.getAccessibleConstructor(HashSet.class);
        Method iteratorMethod = MethodUtils.getAccessibleMethod(iterableClass, "iterator");

        Type iterableType = Type.getType(iterableClass);

        VariableTable.Variable testArg = varTable.getArgument(1);
        VariableTable.Variable listVar = varTable.acquireExtra(iterableType);

        /**
         * Collection it;
         * switch(arg1) {
         *     case 0:
         *         it = new ArrayList()
         *         break;
         *     case 1:
         *         it = new LinkedList()
         *         break;
         *     case 2:
         *         it = new HashSet()
         *         break;
         *     default: throw new RuntimeException("must be 0 or 1");
         * }
         * list.iterator();
         */
        LabelNode invokePoint = new LabelNode();
        InsnList methodInsnList
                = merge(tableSwitch(loadVar(testArg),
                                throwRuntimeException("must be 0 or 1"),
                                0,
                                merge(
                                        construct(arrayListConstructor),
                                        saveVar(listVar),
                                        jumpTo(invokePoint)
                                ),
                                merge(
                                        construct(linkedListConstructor),
                                        saveVar(listVar),
                                        jumpTo(invokePoint)
                                ),
                                merge(
                                        construct(hashSetConstructor),
                                        saveVar(listVar),
                                        jumpTo(invokePoint)
                                )
                        ),
                        addLabel(invokePoint),
                        call(iteratorMethod, loadVar(listVar)),
                        pop(), // discard results of call
                        returnVoid()
                );

        methodNode.instructions = methodInsnList;
        
        
        
        // write out class and read it back in again so maxes and frames can be properly computed for frame analyzer
        SimpleClassWriter writer = new SimpleClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, classRepo);
        classNode.accept(writer);
        byte[] data = writer.toByteArray();
        
        ClassReader cr = new ClassReader(data);
        classNode = new SimpleClassNode();
        cr.accept(classNode, 0);

        
        methodNode = classNode.methods.get(1);
        

        // analyze
        Frame<BasicValue>[] frames;
        try {
            frames = new Analyzer<>(new SimpleVerifier(classRepo)).analyze(classNode.name, methodNode);
        } catch (AnalyzerException ae) {
            throw new IllegalArgumentException("Analyzer failed to analyze method", ae);
        }
        
        
        // get last frame
        Frame frame = frames[frames.length - 1];
        BasicValue basicValue = (BasicValue) frame.getLocal(listVar.getIndex());
        
        
        // ensure that that the local variable for the collection we created in the switch blocks is an abstract type
        Assert.assertEquals("java/util/AbstractCollection", basicValue.getType().getInternalName());
    }

}
