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

import com.offbynull.coroutines.instrumenter.generators.DebugGenerators.MarkerType;
import static com.offbynull.coroutines.instrumenter.testhelpers.TestUtils.getClasspath;
import static com.offbynull.coroutines.instrumenter.testhelpers.TestUtils.loadClassesInZipResourceAndInstrument;
import static com.offbynull.coroutines.instrumenter.testhelpers.TestUtils.readZipFromResource;
import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.Coroutine;
import com.offbynull.coroutines.user.CoroutineRunner;
import com.offbynull.coroutines.user.MethodState;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public abstract class BaseInstrumenterTest {

    protected static final String SANITY_TEST = "SanityTest";
    protected static final String DIFFERENT_STATES_TEST = "MultipleSuspendStatesTest";
    protected static final String NORMAL_INVOKE_TEST = "NormalInvokeTest";
    protected static final String STATIC_INVOKE_TEST = "StaticInvokeTest";
    protected static final String INTERFACE_INVOKE_TEST = "InterfaceInvokeTest";
    protected static final String RECURSIVE_INVOKE_TEST = "RecursiveInvokeTest";
    protected static final String INHERITANCE_INVOKE_TEST = "InheritanceInvokeTest";
    protected static final String RETURN_INVOKE_TEST = "ReturnInvokeTest";
    protected static final String LONG_RETURN_INVOKE_TEST = "LongReturnInvokeTest";
    protected static final String DOUBLE_RETURN_INVOKE_TEST = "DoubleReturnInvokeTest";
    protected static final String LAMBDA_INVOKE_TEST = "LambdaInvokeTest";
    protected static final String CONSTRUCTOR_INVOKE_TEST = "ConstructorInvokeTest";
    protected static final String EXCEPTION_SUSPEND_TEST = "ExceptionSuspendTest";
    protected static final String JSR_EXCEPTION_SUSPEND_TEST = "JsrExceptionSuspendTest";
    protected static final String EXCEPTION_THROW_TEST = "ExceptionThrowTest";
    protected static final String MONITOR_INVOKE_TEST = "MonitorInvokeTest";
    protected static final String UNINITIALIZED_VARIABLE_INVOKE_TEST = "UninitializedVariableInvokeTest";
    protected static final String PEERNETIC_FAILURE_TEST = "PeerneticFailureTest";
    protected static final String SERIALIZABLE_INVOKE_TEST = "SerializableInvokeTest";
    protected static final String NULL_TYPE_IN_LOCAL_VARIABLE_TABLE_INVOKE_TEST = "NullTypeInLocalVariableTableInvokeTest";
    protected static final String NULL_TYPE_IN_OPERAND_STACK_INVOKE_TEST = "NullTypeInOperandStackInvokeTest";
    protected static final String BASIC_TYPE_INVOKE_TEST = "BasicTypeInvokeTest";
    protected static final String EXCEPTION_THEN_CONTINUE_INVOKE_TEST = "ExceptionThenContinueInvokeTest";
    protected static final String EMPTY_CONTINUATION_POINT_INVOKE_TEST = "EmptyContinuationPointInvokeTest";
    protected static final String COMPLEX_TEST = "ComplexTest";

    protected InstrumentationSettings settings;
    
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    
    @Before
    public void before() {
        settings = generateInstrumentationSettings();
    }
    
    protected abstract InstrumentationSettings generateInstrumentationSettings();
    
    protected abstract CoroutineRunner generateCoroutineRunner(Coroutine coroutine);

    @Test
    public void mustProperlyExecuteSanityTest() throws Exception {
        StringBuilder builder = new StringBuilder();

        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(SANITY_TEST + ".zip", settings)) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(SANITY_TEST);
            Coroutine coroutine = ConstructorUtils.invokeConstructor(cls, builder);

            CoroutineRunner runner = generateCoroutineRunner(coroutine);

            assertTrue(runner.execute());
            assertFalse(runner.execute());
            assertTrue(runner.execute()); // coroutine finished executing here
            assertFalse(runner.execute());

            assertEquals("abab", builder.toString());
        }
    }

    @Test
    public void mustProperlySuspendInDifferentStackAndLocalsStatesTest() throws Exception {
        StringBuilder builder = new StringBuilder();

        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(DIFFERENT_STATES_TEST + ".zip", settings)) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(DIFFERENT_STATES_TEST);
            Coroutine coroutine = ConstructorUtils.invokeConstructor(cls, builder);

            CoroutineRunner runner = generateCoroutineRunner(coroutine);

            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertFalse(runner.execute()); // coroutine finished executing here

            assertEquals("ab", builder.toString());
        }
    }

    @Test
    public void mustProperlySuspendWithVirtualMethods() throws Exception {
        performCountTest(NORMAL_INVOKE_TEST, settings);
    }
    
    @Test
    public void mustProperlySuspendWithStaticMethods() throws Exception {
        performCountTest(STATIC_INVOKE_TEST, settings);
    }

    @Test
    public void mustProperlySuspendWithInterfaceMethods() throws Exception {
        performCountTest(INTERFACE_INVOKE_TEST, settings);
    }

    @Test
    public void mustProperlySuspendWithRecursiveMethods() throws Exception {
        performCountTest(RECURSIVE_INVOKE_TEST, settings);
    }

    @Test
    public void mustProperlySuspendWithInheritedMethods() throws Exception {
        performCountTest(INHERITANCE_INVOKE_TEST, settings);
    }

    @Test
    public void mustProperlySuspendWithMethodsThatReturnValues() throws Exception {
        performCountTest(RETURN_INVOKE_TEST, settings);
    }

    @Test
    public void mustProperlySuspendWithMethodsThatOperateOnLongs() throws Exception {
        performCountTest(LONG_RETURN_INVOKE_TEST, settings);
    }

    @Test
    public void mustProperlySuspendWithMethodsThatOperateOnDoubles() throws Exception {
        performDoubleCountTest(DOUBLE_RETURN_INVOKE_TEST, settings);
    }

    @Test
    public void mustProperlySuspendWithNullTypeInLocalVariableTable() throws Exception {
        performCountTest(NULL_TYPE_IN_LOCAL_VARIABLE_TABLE_INVOKE_TEST, settings);
    }
    
    @Test
    public void mustProperlySuspendWithBasicTypesInLocalVariableTableAndOperandStack() throws Exception {
        performCountTest(BASIC_TYPE_INVOKE_TEST, settings);
    }

    @Test
    public void mustGracefullyIgnoreWhenContinuationPointDoesNotInvokeOtherContinuationPoints() throws Exception {
        performCountTest(EMPTY_CONTINUATION_POINT_INVOKE_TEST, settings);
    }

    // Mix of many tests in to a single coroutine
    @Test
    public void mustProperlySuspendInNonTrivialCoroutine() throws Exception {
        performCountTest(COMPLEX_TEST, settings);
    }


    @Test
    public void mustProperlySuspendInNonTrivialCoroutineWhenDebugModeSet() throws Exception {
        performCountTest(COMPLEX_TEST, settings);
    }
    
    @Test
    public void mustProperlyContinueWhenExceptionOccursButIsCaughtBeforeReachingRunner() throws Exception {
        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(EXCEPTION_THEN_CONTINUE_INVOKE_TEST + ".zip", settings)) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(EXCEPTION_THEN_CONTINUE_INVOKE_TEST);
            Coroutine coroutine = ConstructorUtils.invokeConstructor(cls);

            CoroutineRunner runner = generateCoroutineRunner(coroutine);

            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertFalse(runner.execute());
            
            // There's nothing to check. By virtue of it not failing, we know that it worked. Also, it should print out some messages but we
            // can't check to see what got dumped to stdout.
        }
    }
    
    @Test
    public void mustProperlySuspendWithNullTypeInOperandStackTable() throws Exception {
        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(NULL_TYPE_IN_OPERAND_STACK_INVOKE_TEST + ".zip", settings)) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(NULL_TYPE_IN_OPERAND_STACK_INVOKE_TEST);
            Coroutine coroutine = ConstructorUtils.invokeConstructor(cls);

            CoroutineRunner runner = generateCoroutineRunner(coroutine);

            assertTrue(runner.execute());
            assertFalse(runner.execute());
            
            // There's nothing to check. By virtue of it not failing, we know that it worked. Also, it should print out null but we can't
            // check to see what got dumped to stdout.
        }
    }
    
    @Test
    public void mustProperlySuspendWithSerialization() throws Exception {
        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(SERIALIZABLE_INVOKE_TEST + ".zip", settings)) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(SERIALIZABLE_INVOKE_TEST);
            Coroutine coroutine = ConstructorUtils.invokeConstructor(cls, new StringBuilder());

            // Create and run original for a few cycles
            CoroutineRunner originalRunner = generateCoroutineRunner(coroutine);

            assertTrue(originalRunner.execute());
            assertTrue(originalRunner.execute());
            assertTrue(originalRunner.execute());
            assertTrue(originalRunner.execute());
            assertTrue(originalRunner.execute());
            assertTrue(originalRunner.execute());
            
            // Serialize
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(originalRunner);
            oos.close();
            baos.close();
            byte[] serializedCoroutine = baos.toByteArray();
            
            // Deserialize
            ByteArrayInputStream bais = new ByteArrayInputStream(serializedCoroutine);
            ObjectInputStream ois = new ObjectInputStream(bais) {

                @Override
                protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                    try {
                        return super.resolveClass(desc);
                    } catch (ClassNotFoundException cnfe) {
                        return classLoader.loadClass(desc.getName());
                    }
                }
                
            };
            CoroutineRunner deserializedRunner = (CoroutineRunner) ois.readObject();
            
            // Continue running deserialized
            assertTrue(deserializedRunner.execute());
            assertTrue(deserializedRunner.execute());
            assertTrue(deserializedRunner.execute());
            assertTrue(deserializedRunner.execute());
            assertFalse(deserializedRunner.execute()); // coroutine finished executing here
            assertTrue(deserializedRunner.execute());
            assertTrue(deserializedRunner.execute());
            assertTrue(deserializedRunner.execute());

            // Assert everything continued fine with deserialized version
            Object deserializedCoroutine = FieldUtils.readField(deserializedRunner, "coroutine", true);
            StringBuilder deserializedBuilder = (StringBuilder) FieldUtils.readField(deserializedCoroutine, "builder", true);
            
            assertEquals("started\n"
                    + "0\n"
                    + "1\n"
                    + "2\n"
                    + "3\n"
                    + "4\n"
                    + "5\n"
                    + "6\n"
                    + "7\n"
                    + "8\n"
                    + "9\n"
                    + "started\n"
                    + "0\n"
                    + "1\n"
                    + "2\n", deserializedBuilder.toString());
        }
    }
    
    @Test
    public void mustProperlySuspendWithUninitializedLocalVariables() throws Exception {
        StringBuilder builder = new StringBuilder();

        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(UNINITIALIZED_VARIABLE_INVOKE_TEST + ".zip", settings)) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(UNINITIALIZED_VARIABLE_INVOKE_TEST);
            Coroutine coroutine = ConstructorUtils.invokeConstructor(cls, builder);

            CoroutineRunner runner = generateCoroutineRunner(coroutine);

            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
        }
    }

    @Test
    public void mustNotFailWithVerifierErrorWhenRunningAsPerOfAnActor() throws Exception {
        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(PEERNETIC_FAILURE_TEST + ".zip", settings)) {
            Class<?> cls = classLoader.loadClass(PEERNETIC_FAILURE_TEST);
            MethodUtils.invokeStaticMethod(cls, "main", new Object[] { new String[0] });
        }
    }
    
    @Test
    public void mustRejectLambdas() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("INVOKEDYNAMIC instructions are not allowed");
        
        performCountTest(LAMBDA_INVOKE_TEST, settings);
    }

    @Test
    public void mustProperlyReportExceptions() throws Exception {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception thrown during execution");
        
        performCountTest(EXCEPTION_THROW_TEST, settings);
    }

    @Test
    public void mustHaveResetLoadingStateOnException() throws Exception {
        StringBuilder builder = new StringBuilder();
        Continuation continuation = null;
        boolean hit = false;
        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(EXCEPTION_THROW_TEST + ".zip", settings)) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(EXCEPTION_THROW_TEST);
            Coroutine coroutine = ConstructorUtils.invokeConstructor(cls, builder);

            CoroutineRunner runner = generateCoroutineRunner(coroutine);
            continuation = (Continuation) FieldUtils.readField(runner, "continuation", true);
            
            runner.execute();
            runner.execute();
            runner.execute();
            runner.execute();
            runner.execute();
            runner.execute();
        } catch (RuntimeException e) {
            hit = true;
        }

        assertTrue(hit);
        
        MethodState firstPointer = (MethodState) FieldUtils.readField(continuation, "firstPointer", true);
        MethodState nextLoadPointer = (MethodState) FieldUtils.readField(continuation, "nextLoadPointer", true);
        MethodState nextUnloadPointer = (MethodState) FieldUtils.readField(continuation, "nextUnloadPointer", true);
        MethodState firstCutpointPointer = (MethodState) FieldUtils.readField(continuation, "firstCutpointPointer", true);
        assertEquals(2, continuation.getSize());
        assertNotNull(continuation.getSaved(0));
        assertNotNull(continuation.getSaved(1));
        assertNotNull(firstPointer);
        assertNotNull(nextLoadPointer);
        assertTrue(firstPointer == nextLoadPointer);
        assertNull(nextUnloadPointer);
        assertNull(firstCutpointPointer);
    }

    protected void performCountTest(String testClass, InstrumentationSettings settings) throws Exception {
        StringBuilder builder = new StringBuilder();

        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(testClass + ".zip", settings)) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(testClass);
            Coroutine coroutine = ConstructorUtils.invokeConstructor(cls, builder);

            CoroutineRunner runner = generateCoroutineRunner(coroutine);

            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertFalse(runner.execute()); // coroutine finished executing here
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());

            assertEquals("started\n"
                    + "0\n"
                    + "1\n"
                    + "2\n"
                    + "3\n"
                    + "4\n"
                    + "5\n"
                    + "6\n"
                    + "7\n"
                    + "8\n"
                    + "9\n"
                    + "started\n"
                    + "0\n"
                    + "1\n"
                    + "2\n", builder.toString());
        }
    }

    protected void performDoubleCountTest(String testClass, InstrumentationSettings settings) throws Exception {
        StringBuilder builder = new StringBuilder();

        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(testClass + ".zip", settings)) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(testClass);
            Coroutine coroutine = ConstructorUtils.invokeConstructor(cls, builder);

            CoroutineRunner runner = generateCoroutineRunner(coroutine);

            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertFalse(runner.execute()); // coroutine finished executing here
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());

            assertEquals("started\n"
                    + "0.0\n"
                    + "1.0\n"
                    + "2.0\n"
                    + "3.0\n"
                    + "4.0\n"
                    + "5.0\n"
                    + "6.0\n"
                    + "7.0\n"
                    + "8.0\n"
                    + "9.0\n"
                    + "started\n"
                    + "0.0\n"
                    + "1.0\n"
                    + "2.0\n", builder.toString());
        }
    }

    @Test
    public void mustNotInstrumentConstructors() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Instrumentation of constructors not allowed");

        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(CONSTRUCTOR_INVOKE_TEST + ".zip", settings)) {
            // do nothing, exception will occur
        }
    }

    @Test
    public void mustNotDoubleInstrument() throws Exception {
        byte[] classContent =
                readZipFromResource(COMPLEX_TEST + ".zip").entrySet().stream()
                .filter(x -> x.getKey().endsWith(".class"))
                .map(x -> x.getValue())
                .findAny().get();
        List<File> classpath = getClasspath();
        classpath.addAll(classpath);
        
        Instrumenter instrumenter = new Instrumenter(classpath);
        
        byte[] classInstrumented1stPass = instrumenter.instrument(classContent, settings);
        byte[] classInstrumented2stPass = instrumenter.instrument(classInstrumented1stPass, settings);
        
        assertArrayEquals(classInstrumented1stPass, classInstrumented2stPass);
    }

    @Test
    public void mustProperlySuspendInTryCatchFinally() throws Exception {
        StringBuilder builder = new StringBuilder();

        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(EXCEPTION_SUSPEND_TEST + ".zip", settings)) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(EXCEPTION_SUSPEND_TEST);
            Coroutine coroutine = ConstructorUtils.invokeConstructor(cls, builder);

            CoroutineRunner runner = generateCoroutineRunner(coroutine);

            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertFalse(runner.execute()); // coroutine finished executing here

            assertEquals(
                    "START\n"
                    + "IN TRY 1\n"
                    + "IN TRY 2\n"
                    + "IN CATCH 1\n"
                    + "IN CATCH 2\n"
                    + "IN FINALLY 1\n"
                    + "IN FINALLY 2\n"
                    + "END\n", builder.toString());
        }
    }

    // This class file in this test's ZIP was specifically compiled with JDK 1.2.2
    @Test
    public void mustProperlySuspendInTryCatchFinallyWithOldJsrInstructions() throws Exception {
        StringBuffer builder = new StringBuffer();

        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(JSR_EXCEPTION_SUSPEND_TEST + ".zip", settings)) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(JSR_EXCEPTION_SUSPEND_TEST);
            Coroutine coroutine = ConstructorUtils.invokeConstructor(cls, builder);

            CoroutineRunner runner = generateCoroutineRunner(coroutine);

            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertFalse(runner.execute()); // coroutine finished executing here

            assertEquals(
                    "START\n"
                    + "IN TRY 1\n"
                    + "IN TRY 2\n"
                    + "IN CATCH 1\n"
                    + "IN CATCH 2\n"
                    + "IN FINALLY 1\n"
                    + "IN FINALLY 2\n"
                    + "END\n", builder.toString());
        }
    }
    
    @Test
    public void mustKeepTrackOfSynchronizedBlocks() throws Exception {
        LinkedList<String> tracker = new LinkedList<>();
        
        // mon1/mon2/mon3 all point to different objects that are logically equivalent but different objects. Tracking should ignore logical
        // equivallence and instead focus on checking to make sure that they references are the same. We don't want to call MONITOREXIT on
        // the wrong object because it .equals() another object in the list of monitors being tracked.
        Object mon1 = new ArrayList<>();
        Object mon2 = new ArrayList<>();
        Object mon3 = new ArrayList<>();

        // All we're testing here is tracking. It's difficult to test to see if monitors were re-entered/exited.
        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(MONITOR_INVOKE_TEST + ".zip", settings)) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(MONITOR_INVOKE_TEST);
            Coroutine coroutine = ConstructorUtils.invokeConstructor(cls, tracker, mon1, mon2, mon3);

            CoroutineRunner runner = generateCoroutineRunner(coroutine);
            
            // get continuation object so that we can inspect it and make sure its lockstate is what we expect
            Continuation continuation = (Continuation) FieldUtils.readField(runner, "continuation", true);

            assertTrue(runner.execute());
            assertEquals(Arrays.asList("mon1", "mon2", "mon3", "mon1"), tracker);
            assertArrayEquals(new Object[] { mon1 }, continuation.getSaved(0).getLockState().toArray());
            assertArrayEquals(new Object[] { mon2, mon3, mon1 }, continuation.getSaved(1).getLockState().toArray());
            
            assertTrue(runner.execute());
            assertEquals(Arrays.asList("mon1", "mon2", "mon3"), tracker);
            assertArrayEquals(new Object[] { mon1 }, continuation.getSaved(0).getLockState().toArray());
            assertArrayEquals(new Object[] { mon2, mon3 }, continuation.getSaved(1).getLockState().toArray());
            
            assertTrue(runner.execute());
            assertEquals(Arrays.asList("mon1", "mon2"), tracker);
            assertArrayEquals(new Object[] { mon1 }, continuation.getSaved(0).getLockState().toArray());
            assertArrayEquals(new Object[] { mon2 }, continuation.getSaved(1).getLockState().toArray());
            
            assertTrue(runner.execute());
            assertEquals(Arrays.asList("mon1"), tracker);
            assertArrayEquals(new Object[] { mon1 }, continuation.getSaved(0).getLockState().toArray());
            assertArrayEquals(new Object[] { }, continuation.getSaved(1).getLockState().toArray());
            
            assertTrue(runner.execute());
            assertEquals(Arrays.<String>asList(), tracker);
            assertArrayEquals(new Object[] { }, continuation.getSaved(0).getLockState().toArray());
            
            assertFalse(runner.execute()); // coroutine finished executing here            
        }
    }
}
