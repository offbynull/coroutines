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
import static com.offbynull.coroutines.instrumenter.SharedConstants.DOUBLE_RETURN_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.EMPTY_CONTINUATION_POINT_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.INHERITANCE_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.INTERFACE_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.LONG_RETURN_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.NORMAL_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.NULL_TYPE_IN_LOCAL_VARIABLE_TABLE_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.RECURSIVE_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.RETURN_INVOKE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.STATIC_INVOKE_TEST;
import com.offbynull.coroutines.instrumenter.generators.DebugGenerators.MarkerType;
import com.offbynull.coroutines.instrumenter.testhelpers.TestUtils.ClassSerializabler;
import static com.offbynull.coroutines.instrumenter.testhelpers.TestUtils.loadClassesInZipResourceAndInstrument;
import com.offbynull.coroutines.user.Coroutine;
import com.offbynull.coroutines.user.CoroutineReader;
import com.offbynull.coroutines.user.CoroutineRunner;
import com.offbynull.coroutines.user.CoroutineWriter;
import java.net.URLClassLoader;
import java.util.concurrent.ArrayBlockingQueue;
import static org.apache.commons.lang3.reflect.ConstructorUtils.invokeConstructor;
import static org.apache.commons.lang3.reflect.FieldUtils.readField;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class SerializationTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void mustProperlySuspendWithVirtualMethods() throws Exception {
        performIntCountTest(NORMAL_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }
    
    @Test
    public void mustProperlySuspendWithStaticMethods() throws Exception {
        performIntCountTest(STATIC_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }

    @Test
    public void mustProperlySuspendWithInterfaceMethods() throws Exception {
        performIntCountTest(INTERFACE_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }

    @Test
    public void mustProperlySuspendWithRecursiveMethods() throws Exception {
        performIntCountTest(RECURSIVE_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }

    @Test
    public void mustProperlySuspendWithInheritedMethods() throws Exception {
        performIntCountTest(INHERITANCE_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }

    @Test
    public void mustProperlySuspendWithMethodsThatReturnValues() throws Exception {
        performIntCountTest(RETURN_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }

    @Test
    public void mustProperlySuspendWithMethodsThatOperateOnLongs() throws Exception {
        performIntCountTest(LONG_RETURN_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }

    @Test
    public void mustProperlySuspendWithMethodsThatOperateOnDoubles() throws Exception {
        performDoubleCountTest(DOUBLE_RETURN_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }

    @Test
    public void mustProperlySuspendWithNullTypeInLocalVariableTable() throws Exception {
        performIntCountTest(NULL_TYPE_IN_LOCAL_VARIABLE_TABLE_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }
    
    @Test
    public void mustProperlySuspendWithBasicTypesInLocalVariableTableAndOperandStack() throws Exception {
        performIntCountTest(BASIC_TYPE_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }

    @Test
    public void mustGracefullyIgnoreWhenContinuationPointDoesNotInvokeOtherContinuationPoints() throws Exception {
        performIntCountTest(EMPTY_CONTINUATION_POINT_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }

    private void performIntCountTest(String testClass, InstrumentationSettings settings) throws Exception {
        // This test is being wrapped in a new thread where the thread's context classlaoder is being set to the classloader of the zip
        // we're dynamically loading. We need to do this being ObjectInputStream uses the system classloader by default, not the thread's
        // classloader. CoroutineReader has been modified to use the thread's classloader if the system's classloader fails.
        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(testClass + ".zip", settings)) {
            ArrayBlockingQueue<Throwable> threadResult = new ArrayBlockingQueue<>(1);
            Thread thread = new Thread(() -> {
                try {
                    Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(testClass);
                    Coroutine coroutine = invokeConstructor(cls, new StringBuilder());

                    // Create and run original for a few cycles
                    CoroutineRunner runner = new CoroutineRunner(coroutine);

                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertFalse((runner = writeReadExecute(runner)).execute()); // coroutine finished executing here
                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertTrue((runner = writeReadExecute(runner)).execute());

                    // Assert everything continued fine with deserialized version
                    Object deserializedCoroutine = readField(runner, "coroutine", true);
                    StringBuilder deserializedBuilder = (StringBuilder) readField(deserializedCoroutine, "builder", true);

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
                } catch (AssertionError | Exception e) {
                    threadResult.add(e);
                }
            });
            thread.setContextClassLoader(classLoader);
            thread.start();
            thread.join();

            Throwable t = (Throwable) threadResult.peek();
            if (t != null) {
                if (t instanceof Exception) {
                    throw (Exception) t;
                } else if (t instanceof Error) {
                    throw (Error) t;
                } else {
                    throw new RuntimeException();
                }
            }
        }
    }

    private CoroutineRunner writeReadExecute(CoroutineRunner runner) {
        byte[] data = new CoroutineWriter().write(runner);
        CoroutineRunner reconstructedRunner = new CoroutineReader().read(data);
        return reconstructedRunner;
    }

    private void performDoubleCountTest(String testClass, InstrumentationSettings settings) throws Exception {
        // This test is being wrapped in a new thread where the thread's context classlaoder is being set to the classloader of the zip
        // we're dynamically loading. We need to do this being ObjectInputStream uses the system classloader by default, not the thread's
        // classloader. CoroutineReader has been modified to use the thread's classloader if the system's classloader fails.
        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(testClass + ".zip", settings)) {
            ArrayBlockingQueue<Throwable> threadResult = new ArrayBlockingQueue<>(1);
            Thread thread = new Thread(() -> {
                try {
                    Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(testClass);
                    Coroutine coroutine = invokeConstructor(cls, new StringBuilder());

                    // Create and run original for a few cycles
                    CoroutineRunner runner = new CoroutineRunner(coroutine);


                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertFalse((runner = writeReadExecute(runner)).execute()); // coroutine finished executing here
                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertTrue((runner = writeReadExecute(runner)).execute());
                    assertTrue((runner = writeReadExecute(runner)).execute());

                    // Assert everything continued fine with deserialized version
                    Object deserializedCoroutine = readField(runner, "coroutine", true);
                    StringBuilder deserializedBuilder = (StringBuilder) readField(deserializedCoroutine, "builder", true);

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
                            + "2.0\n", deserializedBuilder.toString());
                } catch (AssertionError | Exception e) {
                    threadResult.add(e);
                }
            });
            thread.setContextClassLoader(classLoader);
            thread.start();
            thread.join();

            Throwable t = (Throwable) threadResult.peek();
            if (t != null) {
                if (t instanceof Exception) {
                    throw (Exception) t;
                } else if (t instanceof Error) {
                    throw (Error) t;
                } else {
                    throw new RuntimeException();
                }
            }
        }
    }
}
