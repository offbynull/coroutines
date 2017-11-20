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

import com.offbynull.coroutines.instrumenter.generators.DebugGenerators;
import static com.offbynull.coroutines.instrumenter.testhelpers.TestUtils.loadClassesInZipResourceAndInstrument;
import com.offbynull.coroutines.user.Coroutine;
import com.offbynull.coroutines.user.CoroutineReader;
import com.offbynull.coroutines.user.CoroutineRunner;
import com.offbynull.coroutines.user.CoroutineWriter;
import com.offbynull.coroutines.user.SerializedState.FrameInterceptPoint;
import java.net.URLClassLoader;
import java.util.concurrent.ArrayBlockingQueue;
import static org.apache.commons.lang3.reflect.ConstructorUtils.invokeConstructor;
import static org.apache.commons.lang3.reflect.FieldUtils.readField;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static com.offbynull.coroutines.user.SerializedState.FrameModifier.WRITE;
import static com.offbynull.coroutines.user.SerializedState.FrameModifier.READ;
import com.offbynull.coroutines.user.SerializedState.FrameUpdatePoint;
import static org.junit.Assert.fail;
import static com.offbynull.coroutines.instrumenter.SharedConstants.INTERCEPT_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.UPDATE_TEST;
import static com.offbynull.coroutines.instrumenter.SharedConstants.UPDATE_TEST_MODIFIED;
import static com.offbynull.coroutines.instrumenter.SharedConstants.UPDATE_TEST_ORIGINAL;
import java.util.Arrays;
import org.apache.commons.lang3.mutable.MutableObject;

public final class VersioningTest {

    @Test
    public void mustInterceptOnRead() throws Exception {
        runWrapped(INTERCEPT_TEST, (classLoader) -> {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(INTERCEPT_TEST);

            Coroutine coroutine = invokeConstructor(cls);
            CoroutineRunner runner = new CoroutineRunner(coroutine);

            assertTrue(runner.execute());

            CoroutineReader reader = new CoroutineReader(
                    new FrameInterceptPoint[]{
                        new FrameInterceptPoint(INTERCEPT_TEST, -926730864, 0, (frame, mode) -> {
                            assertEquals(READ, mode);

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

                            return frame.withVariables(varInts, varFloats, varLongs, varDoubles, varObjects, null);
                        })
                    }
            );
            CoroutineWriter writer = new CoroutineWriter();

            byte[] data = writer.write(runner);
            runner = reader.read(data);

            assertFalse(runner.execute());

            StringBuilder sb = (StringBuilder) readField(runner.getCoroutine(), "sb", true);
            assertEquals(
                    "pre_src: InterceptTest 1 \u0002 3 4.0 5 6.0 src\npost_src: InterceptTest -1 \ufffe -3 -4.0 -5 -6.0 dst\n",
                    sb.toString());
        });
    }

    @Test
    public void mustInterceptOnWrite() throws Exception {
        runWrapped(INTERCEPT_TEST, (classLoader) -> {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(INTERCEPT_TEST);

            Coroutine coroutine = invokeConstructor(cls);
            CoroutineRunner runner = new CoroutineRunner(coroutine);

            assertTrue(runner.execute());

            CoroutineReader reader = new CoroutineReader();
            CoroutineWriter writer = new CoroutineWriter(
                    new FrameInterceptPoint[]{
                        new FrameInterceptPoint(INTERCEPT_TEST, -926730864, 0, (frame, mode) -> {
                            assertEquals(WRITE, mode);

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

                            return frame.withVariables(varInts, varFloats, varLongs, varDoubles, varObjects, null);
                        })
                    }
            );

            byte[] data = writer.write(runner);
            runner = reader.read(data);

            assertFalse(runner.execute());

            StringBuilder sb = (StringBuilder) readField(runner.getCoroutine(), "sb", true);
            assertEquals(
                    "pre_src: InterceptTest 1 \u0002 3 4.0 5 6.0 src\npost_src: InterceptTest -1 \ufffe -3 -4.0 -5 -6.0 dst\n",
                    sb.toString());
        });
    }









    @Test
    public void mustProperlyVersionUp() throws Exception {
        MutableObject<byte[]> dataPlaceholder = new MutableObject<>();


        // Run the original and execute it once (there's only 1 suspend in this class)
        runWrapped(UPDATE_TEST_ORIGINAL, (classLoader) -> {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(UPDATE_TEST);

            Coroutine coroutine = invokeConstructor(cls);
            CoroutineRunner runner = new CoroutineRunner(coroutine);

            assertTrue(runner.execute());

            CoroutineWriter writer = new CoroutineWriter();

            byte[] data = writer.write(runner);
            dataPlaceholder.setValue(data);
        });


        // Read it back in to the modified version and execute it again to finish (using frame update modifier to get it loaded to correct
        // frame state)
        runWrapped(UPDATE_TEST_MODIFIED, (classLoader) -> {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(UPDATE_TEST);

            FrameUpdatePoint updateEchoPoint = new FrameUpdatePoint(UPDATE_TEST, -526669244, 0, (frame, mode) -> {
                Object[] varObjects =  frame.getVariables().getObjects();
                
                varObjects = Arrays.copyOf(varObjects, 4);
                varObjects[3] = "_";
                
                return frame
                        .withMethodId(-1238526627)
                        .withObjectVariables(varObjects);
            });
            CoroutineReader reader = new CoroutineReader(new FrameUpdatePoint[] { updateEchoPoint });
            
            byte[] data = dataPlaceholder.getValue();
            CoroutineRunner runner = reader.read(data);
            
            assertFalse(runner.execute());
        });
    }

    @Test
    public void mustProperlyVersionDown() throws Exception {
        MutableObject<byte[]> dataPlaceholder = new MutableObject<>();


        // Run the original and execute it once (there's only 1 suspend in this class), but when you serialize it make sure it serializes
        // to the the modified version and the original version
        runWrapped(UPDATE_TEST_MODIFIED, (classLoader) -> {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(UPDATE_TEST);

            Coroutine coroutine = invokeConstructor(cls);
            CoroutineRunner runner = new CoroutineRunner(coroutine);

            assertTrue(runner.execute());

            // This adds a frame for -526669244 -- remember that the original frame is also saved. So if we read to the original version
            // it will load up what this upgrader computes and returns, but if we read to the modified version it will load up what was
            // PASSED IN to this upgrader.
            FrameUpdatePoint downgradeEchoPoint = new FrameUpdatePoint(UPDATE_TEST, -1238526627, 0, (frame, mode) -> {
                Object[] varObjects =  frame.getVariables().getObjects();
                
                varObjects = Arrays.copyOf(varObjects, 3); // trim the last item
                
                return frame
                        .withMethodId(-526669244)
                        .withObjectVariables(varObjects);
            });
            CoroutineWriter writer = new CoroutineWriter(new FrameUpdatePoint[] { downgradeEchoPoint });

            byte[] data = writer.write(runner);
            dataPlaceholder.setValue(data);
        });


        // Read it back in to the MODIFIED version -- no upgrader should be needed
        runWrapped(UPDATE_TEST_MODIFIED, (classLoader) -> {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(UPDATE_TEST);

            CoroutineReader reader = new CoroutineReader();
            
            byte[] data = dataPlaceholder.getValue();
            CoroutineRunner runner = reader.read(data);
            
            assertFalse(runner.execute());
        });


        // Read it back in to the ORIGINAL version -- no upgrader should be needed because we added a frame for this version when we
        // originally wrote out
        runWrapped(UPDATE_TEST_ORIGINAL, (classLoader) -> {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(UPDATE_TEST);

            CoroutineReader reader = new CoroutineReader();
            
            byte[] data = dataPlaceholder.getValue();
            CoroutineRunner runner = reader.read(data);
            
            assertFalse(runner.execute());
        });
    }
    
    
    
    
    
    
    
    
    @Test(expected = IllegalArgumentException.class)
    public void mustNotAllowMultipleInterceptsOnSameKeyForRead() throws Exception {
        FrameInterceptPoint interceptPoint = new FrameInterceptPoint("fakeName", 12345, 0,
                (frame, mode) -> {
                    fail();
                    return null;
                }
        );

        CoroutineReader reader = new CoroutineReader(
                new FrameInterceptPoint[]{
                    interceptPoint,
                    interceptPoint
                });
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void mustNotAllowMultipleInterceptsOnSameKeyForWrite() throws Exception {
        FrameInterceptPoint interceptPoint1 = new FrameInterceptPoint("fakeName", 12345, 0, (frame, mode) -> null);
        FrameInterceptPoint interceptPoint2 = new FrameInterceptPoint("fakeName", 12345, 0, (frame, mode) -> null);
        
        CoroutineWriter writer = new CoroutineWriter(
                new FrameInterceptPoint[]{
                    interceptPoint1,
                    interceptPoint2
                });
    }

    @Test(expected = IllegalArgumentException.class)
    public void mustNotAllowMultipleUpdatersOnSameKeyForRead() throws Exception {
        FrameInterceptPoint interceptPoint1 = new FrameInterceptPoint("fakeName", 12345, 0, (frame, mode) -> null);
        FrameInterceptPoint interceptPoint2 = new FrameInterceptPoint("fakeName", 12345, 0, (frame, mode) -> null);

        CoroutineReader reader = new CoroutineReader(
                new FrameInterceptPoint[]{
                    interceptPoint1,
                    interceptPoint2
                });
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void mustNotAllowMultipleUpdatersOnSameKeyForWrite() throws Exception {
        FrameUpdatePoint updatePoint1 = new FrameUpdatePoint("fakeName", 12345, 0, (frame, mode) -> null);
        FrameUpdatePoint updatePoint2 = new FrameUpdatePoint("fakeName", 12345, 0, (frame, mode) -> null);
        
        CoroutineWriter writer = new CoroutineWriter(
                new FrameUpdatePoint[]{
                    updatePoint1,
                    updatePoint2
                });
    }
    
    
    
    
    
    
    
    
    
    
    
    
    // Wrap in a new thread where the thread's context classlaoder is being set to the classloader of the zip we're dynamically loading. We
    // need to do this being ObjectInputStream uses the system classloader by default, not the thread's classloader. CoroutineReader has
    // been modified to use the thread's classloader if the system's classloader fails.
    private void runWrapped(String name, WrappedTest test) throws Exception {
        InstrumentationSettings settings = new InstrumentationSettings(DebugGenerators.MarkerType.NONE, false, true);
        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(name + ".zip", settings)) {
            ArrayBlockingQueue<Throwable> threadResult = new ArrayBlockingQueue<>(1);
            Thread thread = new Thread(() -> {
                try {
                    test.run(classLoader);
                } catch (Throwable t) {
                    threadResult.add(t);
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
    
    private interface WrappedTest {
        public void run(ClassLoader classLoader) throws Throwable;
    }
}
