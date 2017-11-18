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
package com.offbynull.coroutines.user;

import com.offbynull.coroutines.user.SerializedState.Data;
import com.offbynull.coroutines.user.SerializedState.Frame;
import com.offbynull.coroutines.user.SerializedState.FrameInterceptPoint;
import com.offbynull.coroutines.user.SerializedState.FrameUpdatePoint;
import com.offbynull.coroutines.user.SerializedState.VersionedFrame;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Writes out (serializes) the current state of a {@link CoroutineRunner} object.
 * @author Kasra Faghihi
 */
public final class CoroutineWriter {
    private final CoroutineSerializer serializer;
    private final Map updatersMap;
    private final Map interceptersMap;
    
    /**
     * Construct a {@link CoroutineWriter} object. Equivalent to calling
     * {@code new CoroutineWriter(new DefaultCoroutineSerializer(), new FrameUpdatePoint[0], new FrameInterceptPoint[0])}.
     */
    public CoroutineWriter() {
        this(new DefaultCoroutineSerializer(), new FrameUpdatePoint[0], new FrameInterceptPoint[0]);
    }

    /**
     * Constructs a {@link CoroutineWriter}. Equivalent to calling
     * {@code new CoroutineWriter(new DefaultCoroutineSerializer(), frameUpdatePoints, new FrameInterceptPoint[0])}.
     * @param frameUpdatePoints frame update points
     * @throws IllegalArgumentException if {@code frameUpdatePoints} contains more than one entry for the same identifier
     * (className/oldMethodId/newMethodId/continuationPoint)
     * @throws NullPointerException if any argument is {@code null}
     */
    public CoroutineWriter(FrameUpdatePoint[] frameUpdatePoints) {
        this(new DefaultCoroutineSerializer(), frameUpdatePoints, new FrameInterceptPoint[0]);
    }

    /**
     * Constructs a {@link CoroutineWriter}. Equivalent to calling
     * {@code new CoroutineWriter(new DefaultCoroutineSerializer(), new FrameUpdatePoint[0], frameInterceptPoints)}.
     * @param frameInterceptPoints frame intercept points
     * @throws IllegalArgumentException if {@code frameInterceptPoints} contains more than one entry for the same identifier
     * (className/methodId/continuationPoint)
     * @throws NullPointerException if any argument is {@code null}
     */
    public CoroutineWriter(FrameInterceptPoint[] frameInterceptPoints) {
        this(new DefaultCoroutineSerializer(), new FrameUpdatePoint[0], frameInterceptPoints);
    }

    /**
     * Constructs a {@link CoroutineWriter}. Equivalent to calling
     * {@code new CoroutineWriter(new DefaultCoroutineSerializer(), frameUpdatePoints, frameInterceptPoints)}.
     * @param frameUpdatePoints frame update points
     * @param frameInterceptPoints frame intercept points
     * @throws IllegalArgumentException if {@code frameUpdatePoints} contains more than one entry for the same identifier
     * (className/oldMethodId/newMethodId/continuationPoint), or if {@code frameInterceptPoints} contains more than one entry for the same
     * identifier (className/methodId/continuationPoint)
     * @throws NullPointerException if any argument is {@code null}
     */
    public CoroutineWriter(FrameUpdatePoint[] frameUpdatePoints, FrameInterceptPoint[] frameInterceptPoints) {
        this(new DefaultCoroutineSerializer(), frameUpdatePoints, frameInterceptPoints);
    }

    /**
     * Constructs a {@link CoroutineWriter} object.
     * @param serializer serializer to write out the coroutine state
     * @param frameUpdatePoints frame update points
     * @param frameInterceptPoints frame intercept points
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if {@code frameUpdatePoints} contains more than one entry for the same identifier
     * (className/oldMethodId/newMethodId/continuationPoint), or if {@code frameInterceptPoints} contains more than one entry for the same
     * identifier (className/methodId/continuationPoint)
     */
    public CoroutineWriter(CoroutineSerializer serializer,
            FrameUpdatePoint[] frameUpdatePoints,
            FrameInterceptPoint[] frameInterceptPoints) {
        if (serializer == null || frameUpdatePoints == null || frameInterceptPoints == null) {
            throw new NullPointerException();
        }

        this.serializer = serializer;
        this.updatersMap = new HashMap();
        this.interceptersMap = new HashMap();

        SerializationUtils.populateUpdatesMapAndInterceptsMap(
                updatersMap, frameUpdatePoints,
                interceptersMap, frameInterceptPoints);
    }
    
    /**
     * Serializes a {@link CoroutineRunner} object as a byte array.
     * @param runner coroutine runner to serialize
     * @return {@code runner} serialized to byte array
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if failed to serialize
     */
    public byte[] write(CoroutineRunner runner) {
        if (runner == null) {
            throw new NullPointerException();
        }

        SerializedState serializeState = deconstruct(runner);
        return serializer.serialize(serializeState);
    }

    /**
     * Deconstructs a {@link CoroutineRunner} object to a serializable state.
     * @param runner coroutine runner to deconstruct
     * @return deconstructed representation of {@link CoroutineRunner}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if failed to deconstruct
     */
    public SerializedState deconstruct(CoroutineRunner runner) {
        if (runner == null) {
            throw new NullPointerException();
        }

        Coroutine coroutine = runner.getCoroutine();
        Continuation cn = runner.getContinuation();

        int size = cn.getSize();
        VersionedFrame[] frames = new VersionedFrame[size];

        MethodState currentMethodState = cn.getSaved(0);
        
        int idx = 0;
        while (currentMethodState != null) {
            // Pull out information from MethoState. We should never modify MethodState values, they will be copied by the Data
            // constructor before being passed to the user for further modification.
            String className = currentMethodState.getClassName();
            int methodId = currentMethodState.getMethodId();
            int continuationPoint = currentMethodState.getContinuationPoint();
            
            LockState monitors = currentMethodState.getLockState();
            
            int[] intVars = ((int[]) currentMethodState.getData()[0]);
            float[] floatVars = ((float[]) currentMethodState.getData()[1]);
            long[] longVars = ((long[]) currentMethodState.getData()[2]);
            double[] doubleVars = ((double[]) currentMethodState.getData()[3]);
            Object[] objectVars = ((Object[]) currentMethodState.getData()[4]);
            
            int[] intOperands = ((int[]) currentMethodState.getData()[5]);
            float[] floatOperands = ((float[]) currentMethodState.getData()[6]);
            long[] longOperands = ((long[]) currentMethodState.getData()[7]);
            double[] doubleOperands = ((double[]) currentMethodState.getData()[8]);
            Object[] objectOperands = ((Object[]) currentMethodState.getData()[9]);


            // Clone the object[] buffers because we need to remove references to the Continuation object for this coroutine.
            objectVars = objectVars == null ? new Object[0] : (Object[]) objectVars.clone();
            int[] continuationPositionsInObjectVars = clearContinuationReferences(objectVars, cn);

            objectOperands = objectOperands == null ? new Object[0] : (Object[]) objectOperands.clone();
            int[] continuationPositionsInObjectOperands = clearContinuationReferences(objectOperands, cn);


            // Create the frame, making sure we create empty arrays for any null references (remember that MethodState var/operand arrays
            // that don't contain any data are set to null due to an optimization).
            Frame serializedFrame = new Frame(
                    className,
                    methodId,
                    continuationPoint,
                    monitors == null ? new Object[0] : monitors.toArray(),
                    new Data(
                            intVars == null ? new int[0] : intVars,
                            floatVars == null ? new float[0] : floatVars,
                            longVars == null ? new long[0] : longVars,
                            doubleVars == null ? new double[0] : doubleVars,
                            objectVars,
                            continuationPositionsInObjectVars),
                    new Data(
                            intOperands == null ? new int[0] : intOperands,
                            floatOperands == null ? new float[0] : floatOperands,
                            longOperands == null ? new long[0] : longOperands,
                            doubleOperands == null ? new double[0] : doubleOperands,
                            objectOperands,
                            continuationPositionsInObjectOperands));

            
            // Add all possible down-versions for frame into a versionedframe object and add it
            VersionedFrame versionedFrame = SerializationUtils.calculateAllPossibleFrameVersions(
                    null,
                    updatersMap,
                    interceptersMap,
                    serializedFrame);
            frames[idx] = versionedFrame;
            idx++;

            currentMethodState = currentMethodState.getNext();
        }
        
        Object context = cn.getContext();
        
        return new SerializedState(coroutine, context, frames);
    }

    private int[] clearContinuationReferences(Object[] objects, Continuation cn) {
        int size = 0;
        for (int i = 0; i < objects.length; i++) {
            if (objects[i] == cn) {
                size++;
            }
        }
        
        int[] indexes = new int[size];
        int indexesPointer = 0;
        for (int i = 0; i < objects.length; i++) {
            if (objects[i] == cn) {
                objects[i] = null;
                indexes[indexesPointer] = i;
                indexesPointer++;
            }
        }
        
        return indexes;
    }

    /**
     * Coroutine serializer.
     */
    public interface CoroutineSerializer {
        /**
         * Serializes a coroutine.
         * @param serializedState state to serialize
         * @return serialized byte array
         * @throws NullPointerException if any argument is {@code null}
         * @throws IllegalArgumentException if failed to serialize
         */
        byte[] serialize(SerializedState serializedState);
    }

    /**
     * Default implementation of {@link CoroutineSerializer} (uses Java's built-in serialization mechanism). This implementation has the
     * following restrictions...
     * <ol>
     * <li>Serialization will fail if you have any synchronized blocks (monitor locks).</li>
     * <li>The classes that make up the current state of your coroutine must be serializable (must implement {@link Serializable}).</li>
     * <li>The variables/operands that make up the current state of your coroutine must be serializable (must either be primitives or
     * implement {@link Serializable}).</li>
     * </ol>
     */
    public static final class DefaultCoroutineSerializer implements CoroutineSerializer {
        
        //CHECKSTYLE.OFF:JavadocMethod - Requires @Override annotation to work, but this is designed for Java 1.4 (no annotations support)
        public byte[] serialize(SerializedState serializedState) {
            if (serializedState == null) {
                throw new NullPointerException();
            }

            VersionedFrame[] frames = serializedState.getFrames();
            for (int i = 0; i < frames.length; i++) {
                Frame[] possibleFrames = frames[i].getFrames();
                for (int j = 0; j < possibleFrames.length; j++) {
                    Frame frame = possibleFrames[j];
                    if (frame.getMonitors().length > 0) {
                        throw new IllegalArgumentException("Monitors not allowed in default serializer");
                    }
                }
            }

            ByteArrayOutputStream baos = null;
            ObjectOutputStream oos = null;
            try {
                baos = new ByteArrayOutputStream();
                oos = new ObjectOutputStream(baos);

                oos.writeObject(serializedState);

                return baos.toByteArray();
            } catch (NotSerializableException nse) {
                throw new IllegalArgumentException(nse);
            } catch (InvalidClassException ice) {
                throw new IllegalArgumentException(ice);
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe); // should never happen
            } finally {
                if (oos != null) {
                    try {
                        oos.close();
                    } catch (IOException ioe) {
                        // do nothing
                    }
                }
                if (baos != null) {
                    try {
                        baos.close();
                    } catch (IOException ioe) {
                        // do nothing
                    }
                }
            }
        }
        //CHECKSTYLE.ON:JavadocMethod
    }
}
