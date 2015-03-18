/*
 * Copyright (c) 2015, Kasra Faghihi, All rights reserved.
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

import static com.offbynull.coroutines.instrumenter.InstructionUtils.ifIntegersEqual;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.loadVar;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.loadStringConst;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.merge;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.returnValue;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.tableSwitch;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.throwException;
import static com.offbynull.coroutines.instrumenter.SearchUtils.findMethodsWithName;
import static com.offbynull.coroutines.instrumenter.TestUtils.readZipResourcesAsClassNodes;
import static com.offbynull.coroutines.instrumenter.TestUtils.createJarAndLoad;
import com.offbynull.coroutines.instrumenter.VariableTable.Variable;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import org.apache.commons.lang3.reflect.MethodUtils;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public final class InstructionUtilsTest {
    private static final String STUB_CLASSNAME = "SimpleStub";
    private static final String STUB_FILENAME = STUB_CLASSNAME + ".class";
    private static final String ZIP_RESOURCE_PATH = STUB_CLASSNAME + ".zip";
    private static final String STUB_METHOD_NAME = "fillMeIn";

    private ClassNode classNode;
    private MethodNode methodNode;
    
    @Before
    public void setUp() throws Exception {
        // Load class, get method
        classNode = readZipResourcesAsClassNodes(ZIP_RESOURCE_PATH).get(STUB_FILENAME);
        methodNode = findMethodsWithName(classNode.methods, STUB_METHOD_NAME).get(0);
    }
    
    @Test
    public void mustCreateAndRunNestedSwitchStatements() throws Exception {
        // Augment signature
        methodNode.desc = Type.getMethodDescriptor(Type.getType(String.class), new Type[] { Type.INT_TYPE, Type.INT_TYPE });
        
        // Initialize variable table
        VariableTable varTable = new VariableTable(classNode, methodNode);
        Variable intVar1 = varTable.getArgument(1);
        Variable intVar2 = varTable.getArgument(2);
        
        // Update method logic
        /**
         * switch(arg1) {
         *    case 0:
         *        throw new RuntimeException("0");
         *    case 1:
         *         throw new RuntimeException("1");
         *    case 2:
         *         switch(arg2) {
         *             case 0:
         *                 throw new RuntimeException("0");
         *             case 1:
         *                 throw new RuntimeException("1");
         *             case 2:
         *                 return "OK!";
         *             default:
         *                 throw new RuntimeException("innerdefault")
         *         }
         *     default:
         *         throw new RuntimeException("default");
         * }
         */
        methodNode.instructions
                = tableSwitch(loadVar(intVar1),
                        throwException("default"),
                        0,
                        throwException("0"),
                        throwException("1"),
                        tableSwitch(loadVar(intVar2),
                                throwException("innerdefault"),
                                0,
                                throwException("inner0"),
                                throwException("inner1"),
                                InstructionUtils.returnValue(Type.getType(String.class), loadStringConst("OK!"))
                        )
                );
        
        // Write to JAR file + load up in classloader -- then execute tests
        try (URLClassLoader cl = createJarAndLoad(classNode)) {
            Object obj = cl.loadClass(STUB_CLASSNAME).newInstance();
            
            assertEquals("OK!", MethodUtils.invokeMethod(obj, STUB_METHOD_NAME, 2, 2));
            
            try {
                MethodUtils.invokeMethod(obj, STUB_METHOD_NAME, 0, 0);
                fail();
            } catch (InvocationTargetException ex) {
                assertEquals("0", ex.getCause().getMessage());
            }

            try {
                MethodUtils.invokeMethod(obj, STUB_METHOD_NAME, 2, 10);
                fail();
            } catch (InvocationTargetException ex) {
                assertEquals("innerdefault", ex.getCause().getMessage());
            }

            try {
                MethodUtils.invokeMethod(obj, STUB_METHOD_NAME, 10, 0);
                fail();
            } catch (InvocationTargetException ex) {
                assertEquals("default", ex.getCause().getMessage());
            }
        }
    }

    @Test
    public void mustCreateAndRunIfStatements() throws Exception {
        // Augment signature
        methodNode.desc = Type.getMethodDescriptor(Type.getType(String.class), new Type[] { Type.INT_TYPE, Type.INT_TYPE });
        
        // Initialize variable table
        VariableTable varTable = new VariableTable(classNode, methodNode);
        Variable intVar1 = varTable.getArgument(1);
        Variable intVar2 = varTable.getArgument(2);
        
        // Update method logic
        /**
         * if (arg1 == arg2) {
         *     return "match";
         * }
         * return "nomatch";
         */
        methodNode.instructions
                = merge(ifIntegersEqual(loadVar(intVar1),
                                loadVar(intVar2),
                                returnValue(Type.getType(String.class), loadStringConst("match"))),
                        returnValue(Type.getType(String.class), loadStringConst("nomatch"))
                );
        
        // Write to JAR file + load up in classloader -- then execute tests
        try (URLClassLoader cl = createJarAndLoad(classNode)) {
            Object obj = cl.loadClass(STUB_CLASSNAME).newInstance();
            
            assertEquals("match", MethodUtils.invokeMethod(obj, STUB_METHOD_NAME, 2, 2));
            assertEquals("nomatch", MethodUtils.invokeMethod(obj, STUB_METHOD_NAME, -2, 2));
            assertEquals("match", MethodUtils.invokeMethod(obj, STUB_METHOD_NAME, -2, -2));
        }
    }
}
