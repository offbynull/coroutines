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

import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.addLabel;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.call;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.construct;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.jumpTo;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.loadVar;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.merge;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.saveVar;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.tableSwitch;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.throwException;
import static com.offbynull.coroutines.instrumenter.testhelpers.TestUtils.readZipFromResource;
import com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.pop;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.returnVoid;
import static com.offbynull.coroutines.instrumenter.testhelpers.TestUtils.createJarAndLoad;

public final class SimpleClassWriterTest {
    
    private ClassNode classNode;
    private MethodNode methodNode;
    
    @Before
    public void setUp() throws IOException {
        byte[] classData = readZipFromResource("SimpleStub.zip").get("SimpleStub.class");
        
        ClassReader classReader = new ClassReader(classData);
        classNode = new ClassNode();
        classReader.accept(classNode, 0);
        
        methodNode = classNode.methods.get(1); // stub should be here
    }

    @Test
    public void testCustomGetCommonSuperClassImplementation() throws Exception {
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
        
        Variable testArg = varTable.getArgument(1);
        Variable listVar = varTable.acquireExtra(iterableType);
        
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
                = merge(tableSwitch(
                                loadVar(testArg),
                                throwException("must be 0 or 1"),
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
                        call(iteratorMethod, InstructionGenerationUtils.loadVar(listVar)),
                        pop(), // discard results of call
                        returnVoid()
                );
        
        methodNode.instructions = methodInsnList;
        
        
        // attemp to write out class -- since we're branching like this it should call getCommonSuperClass() with the types specified in
        // each of the switch cases. getCommonsuperClass() is the logic we explictly override and are testing. createJarAndLoad uses
        // simpleclasswriter
        try (URLClassLoader cl = createJarAndLoad(classNode)) {
            Object obj = cl.loadClass("SimpleStub").newInstance();
            
            // there should not be any verifer errors here
            MethodUtils.invokeMethod(obj, "fillMeIn", 0);
            MethodUtils.invokeMethod(obj, "fillMeIn", 1);
            MethodUtils.invokeMethod(obj, "fillMeIn", 2);
        }
    }
    
}
