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

import static com.offbynull.coroutines.instrumenter.SharedConstants.VERSION_TEST;
import com.offbynull.coroutines.instrumenter.generators.DebugGenerators;
import com.offbynull.coroutines.instrumenter.testhelpers.TestUtils;
import com.offbynull.coroutines.instrumenter.testhelpers.TestUtils.ClassSerializabler;
import static com.offbynull.coroutines.instrumenter.testhelpers.TestUtils.loadClassesInZipResourceAndInstrument;
import com.offbynull.coroutines.user.Coroutine;
import com.offbynull.coroutines.user.CoroutineReader;
import com.offbynull.coroutines.user.CoroutineReader.ContinuationPointUpdater;
import com.offbynull.coroutines.user.CoroutineReader.DefaultCoroutineDeserializer;
import com.offbynull.coroutines.user.CoroutineRunner;
import com.offbynull.coroutines.user.CoroutineWriter;
import com.offbynull.coroutines.user.CoroutineWriter.DefaultCoroutineSerializer;
import java.net.URLClassLoader;
import java.util.concurrent.ArrayBlockingQueue;
import static org.apache.commons.lang3.reflect.ConstructorUtils.invokeConstructor;
import static org.apache.commons.lang3.reflect.FieldUtils.readField;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class CoroutineVersioningTest {
    @Test
    public void mustRunModifierOnDeserialize() throws Exception {
//        Class Name: VersionTest
//        Method Name: run
//        Method ID: 1972618944
//        Method Version: 204733529
//        Parameters: (Lcom/offbynull/coroutines/user/Continuation;)V
//        Return: V
//        ------------------------------------
//        Continuation Point ID: 0    Line: 30   Type: SuspendContinuationPoint
//          varObjects[0]        // LVT index is 0 / name is this / type is LVersionTest;
//          varObjects[1]        // LVT index is 1 / name is c / type is Lcom/offbynull/coroutines/user/Continuation;
//          varInts[0]           // LVT index is 2 / name is _b / type is int
//          varInts[1]           // LVT index is 3 / name is _c / type is int
//          varInts[2]           // LVT index is 4 / name is _i / type is int
//          varFloats[0]         // LVT index is 5 / name is _f / type is float
//          varLongs[0]          // LVT index is 6 / name is _l / type is long
//          varDoubles[0]        // LVT index is 8 / name is _d / type is double
//          varObjects[2]        // LVT index is 10 / name is _s / type is Ljava/lang/String;
//          operandObjects[0]    // operand index is 0 / type is Lcom/offbynull/coroutines/user/Continuation;
        // This test is being wrapped in a new thread where the thread's context classlaoder is being set to the classloader of the zip
        // we're dynamically loading. We need to do this being ObjectInputStream uses the system classloader by default, not the thread's
        // classloader. CoroutineReader has been modified to use the thread's classloader if the system's classloader fails.
        InstrumentationSettings settings = new InstrumentationSettings(DebugGenerators.MarkerType.NONE, false);
        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(VERSION_TEST + ".zip", settings, new ClassSerializabler())) {
            ArrayBlockingQueue<Throwable> threadResult = new ArrayBlockingQueue<>(1);
            Thread thread = new Thread(() -> {
                try {
                    Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(VERSION_TEST);
                    
                    Coroutine coroutine = invokeConstructor(cls);
                    CoroutineRunner runner = new CoroutineRunner(coroutine);
                    
                    assertTrue(runner.execute());
                    
                    CoroutineWriter writer = new CoroutineWriter(new DefaultCoroutineSerializer());
                    CoroutineReader reader = new CoroutineReader(new DefaultCoroutineDeserializer(), new ContinuationPointUpdater[] {
                        new ContinuationPointUpdater(1972618944, -1410922588, -1410922588, 0, frame -> {
                            int[] varInts = frame.getVariables().getInts();
                            float[] varFloats = frame.getVariables().getFloats();
                            double[] varDoubles = frame.getVariables().getDoubles();
                            long[] varLongs = frame.getVariables().getLongs();
                            Object[] varObjects = frame.getVariables().getObjects();
                            varInts[0] = -1; // LVT index is 2 / name is _b / type is int
                            varInts[1] = -2; // LVT index is 3 / name is _c / type is int
                            varInts[2] = -3; // LVT index is 4 / name is _i / type is int
                            varFloats[0] = -4.0f; // LVT index is 5 / name is _f / type is float
                            varLongs[0] = -5L; // LVT index is 6 / name is _l / type is long
                            varDoubles[0] = -6.0; // LVT index is 8 / name is _d / type is double
                            varObjects[2] = "dst"; // LVT index is 10 / name is _s / type is Ljava/lang/String;
                        })
                    });
                    
                    byte[] data = writer.write(runner);
                    runner = reader.read(data);
                    
                    assertFalse(runner.execute());
                    

                    StringBuilder sb = (StringBuilder) readField(runner.getCoroutine(), "sb", true);
                    assertEquals(
                            "pre_src: VersionTest 1 \u0002 3 4.0 5 6.0 src\npost_src: VersionTest -1 \ufffe -3 -4.0 -5 -6.0 dst\n",
                            sb.toString());
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
