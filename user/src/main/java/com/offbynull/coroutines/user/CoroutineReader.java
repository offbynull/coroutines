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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.OptionalDataException;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads in (deserializes) the state of a {@link CoroutineRunner} object.
 * @author Kasra Faghihi
 */
public final class CoroutineReader {
    private final CoroutineDeserializer deserializer;
    private final Map updatersMap;
    private final Map interceptersMap;

    /**
     * Construct a {@link CoroutineReader} object. Equivalent to calling
     * {@code new CoroutineReader(new DefaultCoroutineDeserializer(), new FrameUpdatePoint[0], new FrameInterceptPoint[0])}.
     */
    public CoroutineReader() {
        this(new DefaultCoroutineDeserializer(), new FrameUpdatePoint[0], new FrameInterceptPoint[0]);
    }

    /**
     * Constructs a {@link CoroutineReader}. Equivalent to calling
     * {@code new CoroutineReader(new DefaultCoroutineDeserializer(), frameUpdatePoints, new FrameInterceptPoint[0])}.
     * @param frameUpdatePoints frame update points
     * @throws IllegalArgumentException if {@code frameUpdatePoints} contains more than one entry for the same identifier
     * (className/oldMethodId/newMethodId/continuationPoint)
     * @throws NullPointerException if any argument is {@code null}
     */
    public CoroutineReader(FrameUpdatePoint[] frameUpdatePoints) {
        this(new DefaultCoroutineDeserializer(), frameUpdatePoints, new FrameInterceptPoint[0]);
    }

    /**
     * Constructs a {@link CoroutineReader}. Equivalent to calling
     * {@code new CoroutineReader(new DefaultCoroutineDeserializer(), new FrameUpdatePoint[0], frameInterceptPoints)}.
     * @param frameInterceptPoints frame intercept points
     * @throws IllegalArgumentException if {@code frameInterceptPoints} contains more than one entry for the same identifier
     * (className/methodId/continuationPoint)
     * @throws NullPointerException if any argument is {@code null}
     */
    public CoroutineReader(FrameInterceptPoint[] frameInterceptPoints) {
        this(new DefaultCoroutineDeserializer(), new FrameUpdatePoint[0], frameInterceptPoints);
    }

    /**
     * Constructs a {@link CoroutineReader}. Equivalent to calling
     * {@code new CoroutineReader(new DefaultCoroutineDeserializer(), frameUpdatePoints, frameInterceptPoints)}.
     * @param frameUpdatePoints frame update points
     * @param frameInterceptPoints frame intercept points
     * @throws IllegalArgumentException if {@code frameUpdatePoints} contains more than one entry for the same identifier
     * (className/oldMethodId/newMethodId/continuationPoint), or if {@code frameInterceptPoints} contains more than one entry for the same
     * identifier (className/methodId/continuationPoint)
     * @throws NullPointerException if any argument is {@code null}
     */
    public CoroutineReader(FrameUpdatePoint[] frameUpdatePoints, FrameInterceptPoint[] frameInterceptPoints) {
        this(new DefaultCoroutineDeserializer(), frameUpdatePoints, frameInterceptPoints);
    }

    /**
     * Constructs a {@link CoroutineReader} object.
     * @param deserializer deserializer to write out the coroutine state
     * @param frameUpdatePoints frame update points
     * @param frameInterceptPoints frame intercept points
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if {@code frameUpdatePoints} contains more than one entry for the same identifier
     * (className/oldMethodId/newMethodId/continuationPoint), or if {@code frameInterceptPoints} contains more than one entry for the same
     * identifier (className/methodId/continuationPoint)
     */
    public CoroutineReader(CoroutineDeserializer deserializer,
            FrameUpdatePoint[] frameUpdatePoints,
            FrameInterceptPoint[] frameInterceptPoints) {
        if (deserializer == null || frameUpdatePoints == null || frameInterceptPoints == null) {
            throw new NullPointerException();
        }

        this.deserializer = deserializer;
        this.updatersMap = new HashMap();
        this.interceptersMap = new HashMap();

        SerializationUtils.populateUpdatesMapAndInterceptsMap(updatersMap, frameUpdatePoints, interceptersMap, frameInterceptPoints);
    }

    /**
     * Deserializes a {@link CoroutineRunner} object from a byte array.
     * <p>
     * If you're handling your own deserialization and you simply want to reconstruct the deserialized object to the
     * {@link CoroutineRunner}, use {@link #reconstruct(com.offbynull.coroutines.user.SerializedState) }.
     * @param data byte array to deserialize
     * @return {@code data} deserialized to a {@link CoroutineRunner} object
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if failed to deserialize or deserialized to a state for an unrecognized method (e.g. a method that's
     * state is being deserialized for was changed but no {@link FrameUpdatePoint} was provided to this class's constructor to
     * handle the changes)
     */
    public CoroutineRunner read(byte[] data) {
        if (data == null) {
            throw new NullPointerException();
        }

        SerializedState serializedState = deserializer.deserialize(data);
        return reconstruct(serializedState);
    }

    /**
     * Reconstructs a {@link CoroutineRunner} object from a serializable state.
     * @param state serialized state to reconstruct
     * @return reconstructed {@link CoroutineRunner}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code state} contains a reference / was updated to contain a reference to an
     * unrecognized method (e.g. a method that's state is being reconstructed for was changed but no {@link FrameUpdatePoint} was
     * provided to this class's constructor to handle the changes)
     */
    public CoroutineRunner reconstruct(SerializedState state) {
        if (state == null) {
            throw new NullPointerException();
        }

        try {
            state.validateState();
        } catch (IllegalStateException ise) {
            throw new IllegalArgumentException(ise);
        }

        Object context = state.getContext();

        VersionedFrame[] versionedFrames = state.getFrames();

        Coroutine coroutine = state.getCoroutine();
        Continuation cn = new Continuation();
        cn.setMode(Continuation.MODE_SAVING);
        cn.setContext(context);

        for (int i = versionedFrames.length - 1; i >= 0; i--) {
            VersionedFrame versionedFrame = versionedFrames[i];
            Frame frame = SerializationUtils.calculateCorrectFrameVersion(null, updatersMap, interceptersMap, versionedFrame);

            // Check that a workable frame actually exists
            if (frame == null) {
                throw new IllegalArgumentException("No loaded method or frame updated found for one of the supplied frames");
            }
            
            
            // Construct MethodState
            String className = frame.getClassName();
            int methodId = frame.getMethodId();
            int continuationPoint = frame.getContinuationPointId();

            LockState lockState = new LockState();
            Object[] monitors = frame.getMonitors();
            for (int j = 0; j < monitors.length; j++) {
                Object monitor = monitors[j];

                lockState.enter(monitor);
            }

            Data variables = frame.getVariables();
            Data operands = frame.getOperands();
            Object[] frameData = new Object[10];
            frameData[0] = variables.getInts();
            frameData[1] = variables.getFloats();
            frameData[2] = variables.getLongs();
            frameData[3] = variables.getDoubles();
            frameData[4] = variables.getObjects();
            frameData[5] = operands.getInts();
            frameData[6] = operands.getFloats();
            frameData[7] = operands.getLongs();
            frameData[8] = operands.getDoubles();
            frameData[9] = operands.getObjects();
            
            placeContinuationReferences(variables.getContinuationIndexes(), (Object[]) frameData[4], cn);
            placeContinuationReferences(operands.getContinuationIndexes(), (Object[]) frameData[9], cn);
            
            MethodState methodState = new MethodState(className, methodId, continuationPoint, frameData, lockState);

            
            
            // Place it in the new continuation object.
            cn.pushNewMethodState(methodState);
        }

        if (versionedFrames.length > 0) {
            // The coroutine has executed and has saved state
            cn.successExecutionCycle();
            cn.setMode(Continuation.MODE_LOADING);
        } else {
            // The coroutine hasn't executed / doesn't have saved state
            cn.reset();
        }

        return new CoroutineRunner(coroutine, cn);
    }

    private void placeContinuationReferences(int[] continuationIndexes, Object[] objects, Continuation cn) {
        for (int i = 0; i < continuationIndexes.length; i++) {
            int idx = continuationIndexes[i];
            objects[idx] = cn;
        }
    }

    /**
     * Coroutine deserializer.
     */
    public interface CoroutineDeserializer {
        /**
         * Deserializes a coroutine.
         * @param data byte array to deserialize 
         * @return deserialized state
         * @throws NullPointerException if any argument is {@code null}
         * @throws IllegalArgumentException if failed to serialize
         */
        SerializedState deserialize(byte[] data);
    }
    
    /**
     * Default implementation of {@link CoroutineDeserializer} (uses Java's built-in serialization mechanism). This implementation has the
     * the following restrictions...
     * <ol>
     * <li>Deserialization will fail if you have any synchronized blocks (monitor locks).</li>
     * <li>The classes that make up the current state of your coroutine must be serializable (must implement {@link Serializable}).</li>
     * <li>The variables/operands that make up the current state of your coroutine must be serializable (must either be primitives or
     * implement {@link Serializable}).</li>
     * </ol>
     */
    public static final class DefaultCoroutineDeserializer implements CoroutineDeserializer {

        //CHECKSTYLE.OFF:JavadocMethod - Requires @Override annotation to work, but this is designed for Java 1.4 (no annotations support)
        public SerializedState deserialize(byte[] data) {
            if (data == null) {
                throw new NullPointerException();
            }

            ByteArrayInputStream bais = null;
            ObjectInputStream ois = null;
            try {
                bais = new ByteArrayInputStream(data);
                ois = new ObjectInputStream(bais) {
                    protected Class resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                        try {
                            return super.resolveClass(desc);
                        } catch (ClassNotFoundException cnfe) {
                            // We need this for our test, because we're loading up the coroutine classes in their own classloader and
                            // objectinputstream only uses the system classloader. See the serialization test in InstrumenterTest.
                            return Thread.currentThread().getContextClassLoader().loadClass(desc.getName());
                        }
                    }
                };

                SerializedState serializedState = (SerializedState) ois.readObject();

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
                
                return serializedState;
            } catch (StreamCorruptedException sce) {
                throw new IllegalArgumentException(sce);
            } catch (OptionalDataException ode) {
                throw new IllegalArgumentException(ode);
            } catch (InvalidClassException ice) {
                throw new IllegalArgumentException(ice);
            } catch (ClassNotFoundException cnfe) {
                throw new IllegalArgumentException(cnfe);
            } catch (ClassCastException cce) {
                throw new IllegalArgumentException(cce);
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe); // should never happen
            } finally {
                if (ois != null) {
                    try {
                        ois.close();
                    } catch (IOException ioe) {
                        // do nothing
                    }
                }
                if (bais != null) {
                    try {
                        bais.close();
                    } catch (IOException ioe) {
                        // do nothing
                    }
                }
            }
        }
        //CHECKSTYLE.ON:JavadocMethod
    }
}
