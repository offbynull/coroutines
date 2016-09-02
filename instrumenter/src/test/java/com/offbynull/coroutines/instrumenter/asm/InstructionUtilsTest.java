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

import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.forEach;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.ifIntegersEqual;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.ifObjectsEqual;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadVar;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadStringConst;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.merge;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.returnValue;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.saveVar;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.tableSwitch;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.throwException;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.findMethodsWithName;
import static com.offbynull.coroutines.instrumenter.testhelpers.TestUtils.readZipResourcesAsClassNodes;
import com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable;
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
import static com.offbynull.coroutines.instrumenter.testhelpers.TestUtils.createJarAndLoad;

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

    @Test
    public void mustCreateAndRunForEachStatement() throws Exception {
        // Augment signature
        methodNode.desc = Type.getMethodDescriptor(Type.getType(String.class), new Type[] {
            Type.getType(Object[].class),
            Type.getType(Object.class)
        });
        methodNode.maxLocals += 2; // We've added 2 parameters to the method, and we need to upgrade maxLocals or else varTable will give
                                   // us bad indexes for variables we grab with acquireExtra(). This is because VariableTable uses maxLocals
                                   // to determine at what point to start adding extra local variables.
        
        // Initialize variable table
        VariableTable varTable = new VariableTable(classNode, methodNode);
        Variable objectArrVar = varTable.getArgument(1);
        Variable searchObjVar = varTable.getArgument(2);
        Variable counterVar = varTable.acquireExtra(Type.INT_TYPE);
        Variable arrayLenVar = varTable.acquireExtra(Type.INT_TYPE);
        Variable tempObjectVar = varTable.acquireExtra(Object.class);
        
        // Update method logic
        /**
         * for (Object[] o : arg1) {
         *     if (o == arg2) {
         *         return "match";
         *     }
         * }
         * return "nomatch";
         */
        methodNode.instructions
                = merge(
                        forEach(counterVar, arrayLenVar,
                                loadVar(objectArrVar),
                                merge(
                                        saveVar(tempObjectVar),
                                        ifObjectsEqual(loadVar(tempObjectVar), loadVar(searchObjVar),
                                                returnValue(Type.getType(String.class), loadStringConst("match")))
                                )
                        ),
                        returnValue(Type.getType(String.class), loadStringConst("nomatch"))
                );
        
        // Write to JAR file + load up in classloader -- then execute tests
        try (URLClassLoader cl = createJarAndLoad(classNode)) {
            Object obj = cl.loadClass(STUB_CLASSNAME).newInstance();
            
            Object o1 = new Object();
            Object o2 = new Object();
            Object o3 = new Object();
            
            assertEquals("match", MethodUtils.invokeMethod(obj, STUB_METHOD_NAME, (Object) new Object[] { o1, o2, o3 }, o1));
            assertEquals("match", MethodUtils.invokeMethod(obj, STUB_METHOD_NAME, (Object) new Object[] { o1, o2, o3 }, o2));
            assertEquals("match", MethodUtils.invokeMethod(obj, STUB_METHOD_NAME, (Object) new Object[] { o1, o2, o3 }, o3));
            assertEquals("nomatch", MethodUtils.invokeMethod(obj, STUB_METHOD_NAME, (Object) new Object[] { o1, o2, o3 }, null));
            assertEquals("nomatch", MethodUtils.invokeMethod(obj, STUB_METHOD_NAME, (Object) new Object[] { o1, o2, o3 }, new Object()));
        }
    }
}
