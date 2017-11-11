/*
 * Copyright (c) 2017, Kasra Faghihi, All rights reserved.
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

import static com.offbynull.coroutines.instrumenter.SharedConstants.BASIC_TYPE_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.COMPLEX_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.CONSTRUCTOR_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.DIFFERENT_STATES_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.DOUBLE_RETURN_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.EMPTY_CONTINUATION_POINT_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.EXCEPTION_SUSPEND_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.EXCEPTION_THEN_CONTINUE_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.EXCEPTION_THROW_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.INHERITANCE_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.INTERFACE_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.JSR_EXCEPTION_SUSPEND_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.LAMBDA_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.LONG_RETURN_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.MONITOR_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.NORMAL_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.NULL_TYPE_IN_LOCAL_VARIABLE_TABLE_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.NULL_TYPE_IN_OPERAND_STACK_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.PEERNETIC_FAILURE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.RECURSIVE_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.RETURN_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.SANITY_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.STATIC_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.UNINITIALIZED_VARIABLE_INVOKE_TEST;
import com.offbynull.coroutines.instrumenter.generators.DebugGenerators.MarkerType;
import static com.offbynull.coroutines.instrumenter.testhelpers.TestUtils.getClasspath;
import static com.offbynull.coroutines.instrumenter.testhelpers.TestUtils.loadClassesInZipResourceAndInstrument;
import static com.offbynull.coroutines.instrumenter.testhelpers.TestUtils.readZipFromResource;
import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.Coroutine;
import com.offbynull.coroutines.user.CoroutineRunner;
import com.offbynull.coroutines.user.MethodState;
import java.io.File;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import static org.apache.commons.lang3.reflect.ConstructorUtils.invokeConstructor;
import static org.apache.commons.lang3.reflect.FieldUtils.readField;
import static org.apache.commons.lang3.reflect.MethodUtils.invokeStaticMethod;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class CoroutineInstrumentationTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void mustProperlyExecuteSanityTest() throws Exception {
        StringBuilder builder = new StringBuilder();

        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(SANITY_TEST + ".zip")) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(SANITY_TEST);
            Coroutine coroutine = invokeConstructor(cls, builder);

            CoroutineRunner runner = new CoroutineRunner(coroutine);

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

        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(DIFFERENT_STATES_TEST + ".zip")) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(DIFFERENT_STATES_TEST);
            Coroutine coroutine = invokeConstructor(cls, builder);

            CoroutineRunner runner = new CoroutineRunner(coroutine);

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
        performCountTest(NORMAL_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false));
    }
    
    @Test
    public void mustProperlySuspendWithStaticMethods() throws Exception {
        performCountTest(STATIC_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false));
    }

    @Test
    public void mustProperlySuspendWithInterfaceMethods() throws Exception {
        performCountTest(INTERFACE_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false));
    }

    @Test
    public void mustProperlySuspendWithRecursiveMethods() throws Exception {
        performCountTest(RECURSIVE_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false));
    }

    @Test
    public void mustProperlySuspendWithInheritedMethods() throws Exception {
        performCountTest(INHERITANCE_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false));
    }

    @Test
    public void mustProperlySuspendWithMethodsThatReturnValues() throws Exception {
        performCountTest(RETURN_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false));
    }

    @Test
    public void mustProperlySuspendWithMethodsThatOperateOnLongs() throws Exception {
        performCountTest(LONG_RETURN_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false));
    }

    @Test
    public void mustProperlySuspendWithMethodsThatOperateOnDoubles() throws Exception {
        performDoubleCountTest(DOUBLE_RETURN_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false));
    }

    @Test
    public void mustProperlySuspendWithNullTypeInLocalVariableTable() throws Exception {
        performCountTest(NULL_TYPE_IN_LOCAL_VARIABLE_TABLE_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false));
    }
    
    @Test
    public void mustProperlySuspendWithBasicTypesInLocalVariableTableAndOperandStack() throws Exception {
        performCountTest(BASIC_TYPE_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false));
    }

    @Test
    public void mustGracefullyIgnoreWhenContinuationPointDoesNotInvokeOtherContinuationPoints() throws Exception {
        performCountTest(EMPTY_CONTINUATION_POINT_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false));
    }

    // Mix of many tests in to a single coroutine
    @Test
    public void mustProperlySuspendInNonTrivialCoroutine() throws Exception {
        performCountTest(COMPLEX_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false));
    }


    @Test
    public void mustProperlySuspendInNonTrivialCoroutineWhenDebugModeSet() throws Exception {
        performCountTest(COMPLEX_TEST, new InstrumentationSettings(MarkerType.CONSTANT, true));
    }
    
    @Test
    public void mustProperlyContinueWhenExceptionOccursButIsCaughtBeforeReachingRunner() throws Exception {
        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(EXCEPTION_THEN_CONTINUE_INVOKE_TEST + ".zip")) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(EXCEPTION_THEN_CONTINUE_INVOKE_TEST);
            Coroutine coroutine = invokeConstructor(cls);

            CoroutineRunner runner = new CoroutineRunner(coroutine);

            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertFalse(runner.execute());
            
            // There's nothing to check. By virtue of it not failing, we know that it worked. Also, it should print out some messages but we
            // can't check to see what got dumped to stdout.
        }
    }
    
    @Test
    public void mustProperlySuspendWithNullTypeInOperandStackTable() throws Exception {
        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(NULL_TYPE_IN_OPERAND_STACK_INVOKE_TEST + ".zip")) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(NULL_TYPE_IN_OPERAND_STACK_INVOKE_TEST);
            Coroutine coroutine = invokeConstructor(cls);

            CoroutineRunner runner = new CoroutineRunner(coroutine);

            assertTrue(runner.execute());
            assertFalse(runner.execute());
            
            // There's nothing to check. By virtue of it not failing, we know that it worked. Also, it should print out null but we can't
            // check to see what got dumped to stdout.
        }
    }
    
    @Test
    public void mustProperlySuspendWithUninitializedLocalVariables() throws Exception {
        StringBuilder builder = new StringBuilder();

        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(UNINITIALIZED_VARIABLE_INVOKE_TEST + ".zip")) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(UNINITIALIZED_VARIABLE_INVOKE_TEST);
            Coroutine coroutine = invokeConstructor(cls, builder);

            CoroutineRunner runner = new CoroutineRunner(coroutine);

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
        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(PEERNETIC_FAILURE_TEST + ".zip")) {
            Class<?> cls = classLoader.loadClass(PEERNETIC_FAILURE_TEST);
            invokeStaticMethod(cls, "main", new Object[] { new String[0] });
        }
    }
    
    @Test
    public void mustRejectLambdas() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("INVOKEDYNAMIC instructions are not allowed");
        
        performCountTest(LAMBDA_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false));
    }

    @Test
    public void mustProperlyReportExceptions() throws Exception {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception thrown during execution");
        
        performCountTest(EXCEPTION_THROW_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false));
    }

    @Test
    public void mustHaveResetLoadingStateOnException() throws Exception {
        StringBuilder builder = new StringBuilder();
        Continuation continuation = null;
        boolean hit = false;
        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(EXCEPTION_THROW_TEST + ".zip")) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(EXCEPTION_THROW_TEST);
            Coroutine coroutine = invokeConstructor(cls, builder);

            CoroutineRunner runner = new CoroutineRunner(coroutine);
            continuation = (Continuation) readField(runner, "continuation", true);
            
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
        
        MethodState firstPointer = (MethodState) readField(continuation, "firstPointer", true);
        MethodState nextLoadPointer = (MethodState) readField(continuation, "nextLoadPointer", true);
        MethodState nextUnloadPointer = (MethodState) readField(continuation, "nextUnloadPointer", true);
        MethodState firstCutpointPointer = (MethodState) readField(continuation, "firstCutpointPointer", true);
        assertEquals(2, continuation.getSize());
        assertNotNull(continuation.getSaved(0));
        assertNotNull(continuation.getSaved(1));
        assertNotNull(firstPointer);
        assertNotNull(nextLoadPointer);
        assertTrue(firstPointer == nextLoadPointer);
        assertNull(nextUnloadPointer);
        assertNull(firstCutpointPointer);
    }

    private void performCountTest(String testClass, InstrumentationSettings settings) throws Exception {
        StringBuilder builder = new StringBuilder();

        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(testClass + ".zip", settings)) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(testClass);
            Coroutine coroutine = invokeConstructor(cls, builder);

            CoroutineRunner runner = new CoroutineRunner(coroutine);

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

    private void performDoubleCountTest(String testClass, InstrumentationSettings settings) throws Exception {
        StringBuilder builder = new StringBuilder();

        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(testClass + ".zip", settings)) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(testClass);
            Coroutine coroutine = invokeConstructor(cls, builder);

            CoroutineRunner runner = new CoroutineRunner(coroutine);

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

        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(CONSTRUCTOR_INVOKE_TEST + ".zip")) {
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
        InstrumentationSettings settings = new InstrumentationSettings(MarkerType.NONE, true);
        
        byte[] classInstrumented1stPass = instrumenter.instrument(classContent, settings).getInstrumentedClass();
        byte[] classInstrumented2stPass = instrumenter.instrument(classInstrumented1stPass, settings).getInstrumentedClass();
        
        assertArrayEquals(classInstrumented1stPass, classInstrumented2stPass);
    }

    @Test
    public void mustProperlySuspendInTryCatchFinally() throws Exception {
        StringBuilder builder = new StringBuilder();

        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(EXCEPTION_SUSPEND_TEST + ".zip", new InstrumentationSettings(MarkerType.STDOUT, false))) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(EXCEPTION_SUSPEND_TEST);
            Coroutine coroutine = invokeConstructor(cls, builder);

            CoroutineRunner runner = new CoroutineRunner(coroutine);

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

        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(JSR_EXCEPTION_SUSPEND_TEST + ".zip")) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(JSR_EXCEPTION_SUSPEND_TEST);
            Coroutine coroutine = invokeConstructor(cls, builder);

            CoroutineRunner runner = new CoroutineRunner(coroutine);

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
        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(MONITOR_INVOKE_TEST + ".zip")) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(MONITOR_INVOKE_TEST);
            Coroutine coroutine = invokeConstructor(cls, tracker, mon1, mon2, mon3);

            CoroutineRunner runner = new CoroutineRunner(coroutine);
            
            // get continuation object so that we can inspect it and make sure its lockstate is what we expect
            Continuation continuation = (Continuation) readField(runner, "continuation", true);

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
