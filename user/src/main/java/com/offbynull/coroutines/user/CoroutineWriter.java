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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Writes out (serializes) the current state of a {@link CoroutineRunner} object into a byte array.
 * @author Kasra Faghihi
 */
public final class CoroutineWriter {
    private final CoroutineSerializer serializer;
    
    /**
     * Constructs a {@link CoroutineWriter} object with a {@link DefaultCoroutineSerializer}. Equivalent to calling
     * {@code new CoroutineWriter(new DefaultCoroutineSerializer())}.
     */
    public CoroutineWriter() {
        this(new DefaultCoroutineSerializer());
    }
    
    /**
     * Constructs a {@link CoroutineWriter} object.
     * @param serializer serializer to write out the coroutine state
     * @throws NullPointerException if any argument is {@code null}
     */
    public CoroutineWriter(CoroutineSerializer serializer) {
        if (serializer == null) {
            throw new NullPointerException();
        }
        
        this.serializer = serializer;
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

        Coroutine coroutine = runner.getCoroutine();
        Continuation cn = runner.getContinuation();

        int size = cn.getSize();
        SerializedState.Frame[] frames = new SerializedState.Frame[size];

        MethodState currentMethodState = cn.getSaved(0);
        
        int idx = 0;
        while (currentMethodState != null) {
            // Pull out information from method state. We should never modify method state values, they will be copied by the Data
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
            SerializedState.Frame serializedFrame = new SerializedState.Frame(
                    className,
                    methodId,
                    continuationPoint,
                    monitors == null ? new Object[0] : monitors.toArray(),
                    new SerializedState.Data(
                            intVars == null ? new int[0] : intVars,
                            floatVars == null ? new float[0] : floatVars,
                            longVars == null ? new long[0] : longVars,
                            doubleVars == null ? new double[0] : doubleVars,
                            objectVars,
                            continuationPositionsInObjectVars),
                    new SerializedState.Data(
                            intOperands == null ? new int[0] : intOperands,
                            floatOperands == null ? new float[0] : floatOperands,
                            longOperands == null ? new long[0] : longOperands,
                            doubleOperands == null ? new double[0] : doubleOperands,
                            objectOperands,
                            continuationPositionsInObjectOperands));

            // Insert the frame
            frames[idx] = serializedFrame;
            idx++;

            // Go to next method state
            currentMethodState = currentMethodState.getNext();
        }
        
        Object context = cn.getContext();
        
        return serializer.serialize(new SerializedState(coroutine, context, frames));
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

            SerializedState.Frame[] frames = serializedState.getFrames();
            for (int i = 0; i < frames.length; i++) {
                SerializedState.Frame frame = frames[i];
                if (frame.getMonitors().length > 0) {
                    throw new IllegalArgumentException("Monitors not allowed in default serializer");
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
