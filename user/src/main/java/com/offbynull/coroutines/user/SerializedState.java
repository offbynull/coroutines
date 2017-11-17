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

import java.io.Serializable;

/**
 * {@link CoroutineRunner}'s state translated for serialization.
 * @author Kasra Faghihi
 */
public final class SerializedState implements Serializable {
    private static final long serialVersionUID = 5L;
    
    private final Coroutine coroutine;
    private final Object context;
    private final Frame[] frames;

    /**
     * Constructs a {@link SerializedState} object.
     * @param coroutine coroutine object
     * @param context coroutine context
     * @param frames method states
     * @throws NullPointerException if {@code frames}
     * @throws IllegalArgumentException if any elements of {@code frame} are {@code null} or are otherwise in an invalid state
     */
    public SerializedState(Coroutine coroutine, Object context, Frame[] frames) {
        if (frames == null) {
            throw new NullPointerException();
        }

        this.coroutine = coroutine;
        this.context = context;
        this.frames = frames;

        try {
            validateState(); // sanity check
        } catch (IllegalStateException ise) {
            throw new IllegalArgumentException(ise);
        }
    }

    /**
     * Get coroutine.
     * @return coroutine
     */
    public Coroutine getCoroutine() {
        return coroutine;
    }

    /**
     * Get coroutine context.
     * @return coroutine context
     */
    public Object getContext() {
        return context;
    }

    /**
     * Get coroutine method states.
     * @return coroutine method states
     */
    public Frame[] getFrames() {
        return frames;
    }

    // Because thsi class is being serialized/deserialized, we need to validate that it's correct once we deserialize it. Call this
    // method to do that.
    void validateState() {
        if (frames == null || coroutine == null) {
            throw new IllegalStateException("Bad state");
        }

        for (int i = 0; i < frames.length; i++) {
            if (frames[i] == null) {
                throw new IllegalStateException("Bad state");
            }
        }
    }

    
    /**
     * {@link MethodState}'s and {@link LockState}'s state translated for serialization.
     */
    public static final class Frame implements Serializable {
        private static final long serialVersionUID = 5L;

        private final String className; // this is friendly name -- uses dots instead of slashes to separate names
        private final int methodId;
        private final int methodVersion;
        private final int continuationPointId;
        private final Object[] monitors;
        private final Data variables;
        private final Data operands;

        /**
         * Constructs a {@link Frame} object.
         * @param className class name
         * @param methodId method id
         * @param methodVersion method version
         * @param continuationPointId continuation point id
         * @param monitors monitor locks
         * @param variables lock variable table values
         * @param operands operand stack values
         * @throws NullPointerException if any argument is {@code null}
         * @throws IllegalArgumentException if any elements in {@code monitors} are {@code null} or if either {@code variables} or
         * {@code operands} are in an invalid state
         */
        public Frame(
                String className,
                int methodId,
                int methodVersion,
                int continuationPointId,
                Object[] monitors,
                Data variables,
                Data operands) {
            if (className == null || monitors == null || variables == null || operands == null) {
                throw new NullPointerException();
            }
            
            this.className = className;
            this.methodId = methodId;
            this.methodVersion = methodVersion;
            this.continuationPointId = continuationPointId;
            this.monitors = (Object[]) monitors.clone();
            this.variables = variables;
            this.operands = operands;

            try {
                validateState(); // sanity check
            } catch (IllegalStateException ise) {
                throw new IllegalArgumentException(ise);
            }
        }

        /**
         * Get class name.
         * @return class name
         */
        public String getClassName() {
            return className;
        }

        /**
         * Get method ID.
         * @return method ID
         */
        public int getMethodId() {
            return methodId;
        }

        /**
         * Get method version.
         * @return method version
         */
        public int getMethodVersion() {
            return methodVersion;
        }

        /**
         * Get continuation point id.
         * @return continuation point id
         */
        public int getContinuationPointId() {
            return continuationPointId;
        }

        /**
         * Get monitor locks.
         * @return monitor locks
         */
        public Object[] getMonitors() {
            return monitors;
        }

        /**
         * Get variables.
         * @return variables
         */
        public Data getVariables() {
            return variables;
        }

        /**
         * Get operands.
         * @return operands
         */
        public Data getOperands() {
            return operands;
        }

        void validateState() {
            if (continuationPointId < 0) {
                throw new IllegalStateException();
            }

            for (int i = 0; i < monitors.length; i++) {
                if (monitors[i] == null) {
                    throw new IllegalStateException();
                }
            }

            variables.validateState();
            operands.validateState();
        }
    }

    /**
     * Data bundle.
     */
    public static final class Data implements Serializable {
        private static final long serialVersionUID = 5L;
        
        private int[] ints;
        private float[] floats;
        private long[] longs;
        private double[] doubles;
        private Object[] objects;

        private int[] continuationIndexes;

        /**
         * Construct a {@link Data} object.
         * @param ints int values
         * @param floats float values
         * @param longs long values
         * @param doubles double values
         * @param objects object values
         * @param continuationIndexes position within {@link objects} where the object pointed to the original {@code Continuation} for the
         * coroutine
         * @throws NullPointerException if any argument is {@code null}
         * @throws IllegalArgumentException if {@code continuationIndexes} points to out of bounds indexes within {@code objects} or if
         * if {@code continuationIndexes} points to non-null indexes within {@code objects}
         */
        public Data(int[] ints, float[] floats, long[] longs, double[] doubles, Object[] objects, int[] continuationIndexes) {
            if (ints == null || floats == null || longs == null || doubles == null || objects == null || continuationIndexes == null) {
                throw new NullPointerException();
            }

            // Clone all these because the initial inputs point to values in MethodState + the user has the opportuntity to change these
            // values before serialization.
            this.ints = (int[]) ints.clone();
            this.floats = (float[]) floats.clone();
            this.longs = (long[]) longs.clone();
            this.doubles = (double[]) doubles.clone();
            this.objects = (Object[]) objects.clone();
            this.continuationIndexes = (int[]) continuationIndexes.clone();

            try {
                validateState(); // sanity check
            } catch (IllegalStateException ise) {
                throw new IllegalArgumentException(ise);
            }
        }

        /**
         * Get int values.
         * @return int values
         */
        public int[] getInts() {
            return ints;
        }

        /**
         * Get float values.
         * @return float values
         */
        public float[] getFloats() {
            return floats;
        }

        /**
         * Get long values.
         * @return long values
         */
        public long[] getLongs() {
            return longs;
        }

        /**
         * Get double values.
         * @return double values
         */
        public double[] getDoubles() {
            return doubles;
        }

        /**
         * Get object values.
         * @return object values
         */
        public Object[] getObjects() {
            return objects;
        }

        /**
         * Get positions within objects array where the object pointed to the original {@link Continuation} for the coroutine.
         * @return positions within {@link #getObjects() } that point to the original {@link Continuation}
         */
        public int[] getContinuationIndexes() {
            return (int[]) continuationIndexes.clone();
        }

        /**
         * Set int values.
         * @param ints int values
         * @throws NullPointerException if any argument is {@code null}
         */
        public void setInts(int[] ints) {
            if (ints == null) {
                throw new NullPointerException();
            }
            this.ints = ints;
        }

        /**
         * Set float values.
         * @param floats float values
         * @throws NullPointerException if any argument is {@code null}
         */
        public void setFloats(float[] floats) {
            if (floats == null) {
                throw new NullPointerException();
            }
            this.floats = floats;
        }

        /**
         * Set long values.
         * @param longs long values
         * @throws NullPointerException if any argument is {@code null}
         */
        public void setLongs(long[] longs) {
            if (longs == null) {
                throw new NullPointerException();
            }
            this.longs = longs;
        }

        /**
         * Set double values.
         * @param doubles double values
         * @throws NullPointerException if any argument is {@code null}
         */
        public void setDoubles(double[] doubles) {
            if (doubles == null) {
                throw new NullPointerException();
            }
            this.doubles = doubles;
        }

        /**
         * Set object values.
         * @param objects object values
         * @throws NullPointerException if any argument is {@code null}
         */
        public void setObjects(Object[] objects) {
            if (objects == null) {
                throw new NullPointerException();
            }
            this.objects = objects;
        }

        /**
         * Set positions within objects array where the object pointed to the original {@link Continuation} for the coroutine.
         * @param continuationIndexes positions within {@link #getObjects() } that point to the original {@link Continuation}
         * @throws NullPointerException if any argument is {@code null}
         */
        public void setContinuationIndexes(int[] continuationIndexes) {
            if (continuationIndexes == null) {
                throw new NullPointerException();
            }
            this.continuationIndexes = continuationIndexes;
        }

        // Because thsi class is being serialized/deserialized, we need to validate that it's correct once we deserialize it. Call this
        // method to do that.
        void validateState() {
            if (ints == null
                    || floats == null
                    || longs == null
                    || doubles == null
                    || objects == null
                    || continuationIndexes == null) {
                throw new IllegalStateException("Bad state");
            }

            for (int i = 0; i < continuationIndexes.length; i++) {
                int pos = continuationIndexes[i];
                if (pos < 0 || pos >= objects.length) {
                    throw new IllegalStateException("Bad state");
                }
                if (objects[pos] != null) {
                    throw new IllegalStateException("Bad state");
                }
            }
        }
    }
}
