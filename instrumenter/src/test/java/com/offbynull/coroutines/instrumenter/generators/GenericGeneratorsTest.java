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
package com.offbynull.coroutines.instrumenter.generators;

import com.offbynull.coroutines.instrumenter.asm.VariableTable;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.call;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.construct;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.ifIntegersEqual;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.ifObjectsEqual;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.loadVar;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.loadStringConst;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.merge;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.returnValue;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.saveVar;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.tableSwitch;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.findMethodsWithName;
import static com.offbynull.coroutines.instrumenter.testhelpers.TestUtils.readZipResourcesAsClassNodes;
import com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.throwRuntimeException;
import static com.offbynull.coroutines.instrumenter.testhelpers.TestUtils.createJarAndLoad;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.forEachLoop;

public final class GenericGeneratorsTest {
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
                        throwRuntimeException("default"),
                        0,
                        throwRuntimeException("0"),
                        throwRuntimeException("1"),
                        tableSwitch(loadVar(intVar2),
                                throwRuntimeException("innerdefault"),
                                0,
                                throwRuntimeException("inner0"),
                                throwRuntimeException("inner1"),
                                GenericGenerators.returnValue(Type.getType(String.class), loadStringConst("OK!"))
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
    public void mustCreateAndRunIfIntStatements() throws Exception {
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
    public void mustCreateAndRunIfObjectStatements() throws Exception {
        // Augment signature
        methodNode.desc = Type.getMethodDescriptor(
                Type.getType(String.class),
                new Type[] { Type.getType(Object.class), Type.getType(Object.class) });
        
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
                = merge(
                        ifObjectsEqual(
                                loadVar(intVar1),
                                loadVar(intVar2),
                                returnValue(Type.getType(String.class), loadStringConst("match"))),
                        returnValue(Type.getType(String.class), loadStringConst("nomatch"))
                );
        
        Object testObj1 = "test1";
        Object testObj2 = "test2";
        // Write to JAR file + load up in classloader -- then execute tests
        try (URLClassLoader cl = createJarAndLoad(classNode)) {
            Object obj = cl.loadClass(STUB_CLASSNAME).newInstance();
            
            assertEquals("match", MethodUtils.invokeMethod(obj, STUB_METHOD_NAME, testObj1, testObj1));
            assertEquals("nomatch", MethodUtils.invokeMethod(obj, STUB_METHOD_NAME, testObj1, testObj2));
            assertEquals("match", MethodUtils.invokeMethod(obj, STUB_METHOD_NAME, testObj2, testObj2));
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
                = merge(forEachLoop(counterVar, arrayLenVar,
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
    
    @Test
    public void mustConstructAndCall() throws Exception {
        // Augment signature
        methodNode.desc = Type.getMethodDescriptor(Type.getType(String.class), new Type[] { });
        
        // Initialize variable table
        VariableTable varTable = new VariableTable(classNode, methodNode);
        Variable sbVar = varTable.acquireExtra(StringBuilder.class);
        Variable retVar = varTable.acquireExtra(String.class);
        
        // Update method logic
        /**
         * return new StringBuilder().append("hi!").toString()
         */
        methodNode.instructions
                = merge(
                        construct(StringBuilder.class.getConstructor()),
                        saveVar(sbVar),
                        call(StringBuilder.class.getMethod("append", String.class), loadVar(sbVar), loadStringConst("hi!")),
                        call(StringBuilder.class.getMethod("toString"), loadVar(sbVar)),
                        saveVar(retVar),
                        returnValue(Type.getType(String.class), loadVar(retVar))
                );
        
        // Write to JAR file + load up in classloader -- then execute tests
        try (URLClassLoader cl = createJarAndLoad(classNode)) {
            Object obj = cl.loadClass(STUB_CLASSNAME).newInstance();
            
            assertEquals("hi!", MethodUtils.invokeMethod(obj, STUB_METHOD_NAME));
        }
    }
}
