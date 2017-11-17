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
 * Reads in (deserializes) the state of a {@link CoroutineRunner} object from a byte array.
 * @author Kasra Faghihi
 */
public final class CoroutineReader {
    private final CoroutineDeserializer deserializer;
    private final Map updateMap;

    /**
     * Construct a {@link CoroutineReader} object with {@link DefaultCoroutineDeserializer}. Equivalent to calling
     * {@code new CoroutineReader(new DefaultCoroutineSerializer())}.
     */
    public CoroutineReader() {
        this(new DefaultCoroutineDeserializer());
    }

    /**
     * Constructs a {@link CoroutineReader} object with a custom coroutine deserializer. Equivalent to calling
     * {@code new CoroutineReader(deserializer, new ContinuationPointUpdater[0])}.
     * @param deserializer deserializer to write out the coroutine state
     * @throws NullPointerException if any argument is {@code null}
     */
    public CoroutineReader(CoroutineDeserializer deserializer) {
        this(deserializer, new ContinuationPointUpdater[0]);
    }

    /**
     * Constructs a {@link CoroutineReader} object.
     * @param deserializer deserializer to write out the coroutine state
     * @param continuationPointUpdaters continuation point updaters
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if {@code continuationPointUpdaters} contains more than one entry for the same identifier
     * (className/methodId/oldMethodVersion/continuationPoint)
     */
    public CoroutineReader(CoroutineDeserializer deserializer, ContinuationPointUpdater[] continuationPointUpdaters) {
        if (deserializer == null || continuationPointUpdaters == null) {
            throw new NullPointerException();
        }

        this.deserializer = deserializer;
        this.updateMap = new HashMap();

        for (int i = 0; i < continuationPointUpdaters.length; i++) {
            ContinuationPointUpdater continuationPointUpdater = continuationPointUpdaters[i];
            if (continuationPointUpdater == null) {
                throw new NullPointerException();
            }

            InternalKey key = new InternalKey(
                    continuationPointUpdater.className,
                    continuationPointUpdater.oldMethodId,
                    continuationPointUpdater.continuationPointId);
            InternalValue value = new InternalValue(
                    continuationPointUpdater.newMethodId,
                    continuationPointUpdater.frameModifier);
            
            Object oldKey = updateMap.put(key, value);
            if (oldKey != null) {
                throw new IllegalArgumentException("Continuation point for identifier already exists: "
                        + "className=" + continuationPointUpdater.className + ", "
                        + "oldMethodId=" + continuationPointUpdater.oldMethodId + ", "
                        + "newMethodId=" + continuationPointUpdater.newMethodId + ", "
                        + "continuationPoint=" + continuationPointUpdater.continuationPointId);
            }
        }
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
     * state is being deserialized for was changed but no {@link ContinuationPointUpdater} was provided to this class's constructor to
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
     * @param serializedState serialized state to reconstruct
     * @return reconstructed {@link CoroutineRunner}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code serializedState} contains a reference / was updated to contain a reference to an
     * unrecognized method (e.g. a method that's state is being reconstructed for was changed but no {@link ContinuationPointUpdater} was
     * provided to this class's constructor to handle the changes)
     */
    public CoroutineRunner reconstruct(SerializedState serializedState) {
        if (serializedState == null) {
            throw new NullPointerException();
        }

        try {
            serializedState.validateState();
        } catch (IllegalStateException ise) {
            throw new IllegalArgumentException(ise);
        }

        Object context = serializedState.getContext();

        SerializedState.Frame[] serializedFrames = serializedState.getFrames();

        Coroutine coroutine = serializedState.getCoroutine();
        Continuation cn = new Continuation();
        cn.setMode(Continuation.MODE_SAVING);
        cn.setContext(context);

        for (int i = serializedFrames.length - 1; i >= 0; i--) {
            SerializedState.Frame serializedFrame = serializedFrames[i];

            String className = serializedFrame.getClassName();
            int methodId = serializedFrame.getMethodId();
            int continuationPoint = serializedFrame.getContinuationPointId();

            InternalKey internalKey = new InternalKey(className, methodId, continuationPoint);
            InternalValue internalValue = (InternalValue) updateMap.get(internalKey);
            while (internalValue != null) {
                internalValue.frameModifier.modifyFrame(serializedFrame);
                methodId = internalValue.newMethodId;

                // This case happens if all we want to do is update values in the frame. If we don't catch and break we'll get into an
                // infinite loop
                if (internalKey.methodId == internalValue.newMethodId) {
                    break;
                }

                internalKey = new InternalKey(className, methodId, continuationPoint);
                internalValue = (InternalValue) updateMap.get(internalKey);                
            }

            // Check that we're deserialzing to for the correct version of the class
            

            LockState lockState = new LockState();
            Object[] monitors = serializedFrame.getMonitors();
            for (int j = 0; j < monitors.length; j++) {
                Object monitor = monitors[j];

                lockState.enter(monitor);
            }

            SerializedState.Data variables = serializedFrame.getVariables();
            SerializedState.Data operands = serializedFrame.getOperands();
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
            boolean correctVersion = methodState.isValid(null);
            if (!correctVersion) {
                throw new IllegalArgumentException("Method not found for frame state "
                        + "className: " + className + ", "
                        + "methodId: " + methodId);
            }
            cn.pushNewMethodState(methodState);
        }

        if (serializedFrames.length > 0) {
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
     * Continuation point updater.
     */
    public static final class ContinuationPointUpdater {

        private final String className;
        private final int oldMethodId;
        private final int newMethodId;
        private final int continuationPointId;
        private final FrameModifier frameModifier;

        /**
         * Constructs a {@link ContinuationPointUpdater} object.
         * @param className class name for the continuation point
         * @param oldMethodId old method id for the continuation point (used for identification)
         * @param newMethodId new method id for the continuation point (used as replacement)
         * @param continuationPointId continuation point ID
         * @param frameModifier logic to modify the frame's contents to the new version
         * @throws NullPointerException if any argument is {@code null}
         * @throws IllegalArgumentException if {@code continuationPointId < 0}
         */
        public ContinuationPointUpdater(String className, int oldMethodId, int newMethodId, int continuationPointId,
                FrameModifier frameModifier) {
            if (frameModifier == null) {
                throw new NullPointerException();
            }
            if (continuationPointId < 0) {
                throw new IllegalArgumentException("Negative continuation point");
            }

            this.className = className;
            this.oldMethodId = oldMethodId;
            this.newMethodId = newMethodId;
            this.continuationPointId = continuationPointId;
            this.frameModifier = frameModifier;
        }
    }

    /**
     * Frame modifier.
     */
    public interface FrameModifier {

        /**
         * Called when a frame needs to be modified.
         * @param frame frame to modify
         */
        void modifyFrame(SerializedState.Frame frame);
    }

    private static final class InternalKey {

        private final String className;
        private final int methodId;
        private final int continuationPointId;

        InternalKey(String className, int methodId, int continuationPointId) {
            if (className == null) {
                throw new NullPointerException();
            }

            this.className = className;
            this.methodId = methodId;
            this.continuationPointId = continuationPointId;
        }

        public int hashCode() {
            int hash = 7;
            hash = 71 * hash + (this.className != null ? this.className.hashCode() : 0);
            hash = 71 * hash + this.methodId;
            hash = 71 * hash + this.continuationPointId;
            return hash;
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final InternalKey other = (InternalKey) obj;
            if (this.methodId != other.methodId) {
                return false;
            }
            if (this.continuationPointId != other.continuationPointId) {
                return false;
            }
            if ((this.className == null) ? (other.className != null) : !this.className.equals(other.className)) {
                return false;
            }
            return true;
        }
    }
    
    private static final class InternalValue {
        private final int newMethodId;
        private final FrameModifier frameModifier;

        InternalValue(int newMethodId, FrameModifier frameModifier) {
            if (frameModifier == null) {
                throw new NullPointerException();
            }
            this.newMethodId = newMethodId;
            this.frameModifier = frameModifier;
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

                SerializedState.Frame[] frames = serializedState.getFrames();
                for (int i = 0; i < frames.length; i++) {
                    SerializedState.Frame frame = frames[i];
                    if (frame.getMonitors().length > 0) {
                        throw new IllegalArgumentException("Monitors not allowed in default serializer");
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
