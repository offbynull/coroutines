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

import com.offbynull.coroutines.user.SerializationUtils.FrameUpdatePointKey;
import com.offbynull.coroutines.user.SerializationUtils.FrameUpdatePointValue;
import java.io.Serializable;

/**
 * {@link CoroutineRunner}'s state translated for serialization.
 * @author Kasra Faghihi
 */
public final class SerializedState implements Serializable {
    private static final long serialVersionUID = 6L;
    
    private final Coroutine coroutine;
    private final Object context;
    private final VersionedFrame[] frames; // at each frame, we can have mulitple frame states (for older/newer versions)

    /**
     * Constructs a {@link SerializedState} object.
     * @param coroutine coroutine object
     * @param context coroutine context
     * @param frames method states
     * @throws NullPointerException if {@code frames}
     * @throws IllegalArgumentException if any elements of {@code frame} are {@code null} or are otherwise in an invalid state
     */
    public SerializedState(Coroutine coroutine, Object context, VersionedFrame[] frames) {
        if (frames == null) {
            throw new NullPointerException();
        }

        this.coroutine = coroutine;
        this.context = context;
        this.frames = (VersionedFrame[]) frames.clone();

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
    public VersionedFrame[] getFrames() {
        return (VersionedFrame[]) frames.clone();
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
            frames[i].validateState();
        }
    }

    
    /**
     * Collection of {@link Frame}s that are for different versions of the same method and continuation point combination.
     */
    public static final class VersionedFrame implements Serializable {
        private static final long serialVersionUID = 6L;
        
        private final Frame[] frames;

        /**
         * Construct a {@link VersionedFrame} from a single frame.
         * @param frame frame
         * @throws NullPointerException if any argument is {@code null}
         */
        public VersionedFrame(Frame frame) {
            this(new Frame[] {frame});
        }


        /**
         * Construct a {@link VersionedFrame} from multiple frame.
         * @param frames frames
         * @throws NullPointerException if any argument is {@code null} or contains {@code null}
         * @throws IllegalArgumentException if {@code frames} is empty, or all the frames in {@code frames} aren't for the same class name,
         * or if {@code frames} contains duplicates
         */
        public VersionedFrame(Frame[] frames) {
            if (frames == null) {
                throw new NullPointerException();
            }

            this.frames = (Frame[]) frames.clone();

            try {
                validateState(); // sanity check
            } catch (IllegalStateException ise) {
                throw new IllegalArgumentException(ise);
            }
        }

        /**
         * Get frames.
         * @return frames
         */
        public Frame[] getFrames() {
            return (Frame[]) frames.clone();
        }

        void validateState() {
            if (frames == null) {
                throw new NullPointerException();
            }

            if (frames.length == 0) {
                throw new IllegalStateException("Bad state");
            }

            // validate states and make sure all the frames belong to the same class
            String clsName = frames[0].className;
            for (int i = 0; i < frames.length; i++) {
                frames[i].validateState();
                if (!clsName.equals(frames[i].className)) {
                    throw new IllegalStateException("Bad state");
                }
            }
            
            if (SerializationUtils.findDuplicates(frames)) {
                throw new IllegalStateException("Bad state");
            }
        }
    }

    
    /**
     * {@link MethodState}'s and {@link LockState}'s state translated for serialization.
     */
    public static final class Frame implements Serializable {
        private static final long serialVersionUID = 6L;

        private final String className; // this is friendly name -- uses dots instead of slashes to separate names
        private final int methodId;
        private final int continuationPointId;
        private final Object[] monitors;
        private final Data variables;
        private final Data operands;

        /**
         * Constructs a {@link Frame} object.
         * @param className class name
         * @param methodId method id
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
                int continuationPointId,
                Object[] monitors,
                Data variables,
                Data operands) {
            if (className == null || monitors == null || variables == null || operands == null) {
                throw new NullPointerException();
            }
            
            this.className = className;
            this.methodId = methodId;
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
            return (Object[]) monitors.clone();
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

        /**
         * Helper to copy this frame but with potentially new variables.
         * @param ints new ints (or {@code null} if should be kept the same)
         * @param floats new floats (or {@code null} if should be kept the same)
         * @param longs new longs (or {@code null} if should be kept the same)
         * @param doubles new doubles (or {@code null} if should be kept the same)
         * @param objects new objects (or {@code null} if should be kept the same)
         * @param continuationIndexes new continuation indexes (or {@code null} if should be kept the same)
         * @return new frame
         * @throws IllegalArgumentException if {@code continuationIndexes} points to out of bounds indexes within {@code objects} or if
         * if {@code continuationIndexes} points to non-null indexes within {@code objects}
         */
        public Frame withVariables(
                int[] ints,
                float[] floats,
                long[] longs,
                double[] doubles,
                Object[] objects,
                int[] continuationIndexes) {
            return new Frame(
                    className,
                    methodId,
                    continuationPointId,
                    monitors,
                    new Data(
                            ints == null ? variables.ints : ints, // copied by Data constructor
                            floats == null ? variables.floats : floats, // copied by Data constructor
                            longs == null ? variables.longs : longs, // copied by Data constructor
                            doubles == null ? variables.doubles : doubles, // copied by Data constructor
                            objects == null ? variables.objects : objects, // copied by Data constructor
                            continuationIndexes == null ? variables.continuationIndexes : continuationIndexes // copied by Data constructor
                    ),
                    operands);
        }

        /**
         * Helper to copy this frame but with new int variables.
         * @param ints new ints
         * @return new frame
         * @throws NullPointerException if any argument is {@code null}
         */
        public Frame withIntVariables(int[] ints) {
            return new Frame(
                    className,
                    methodId,
                    continuationPointId,
                    monitors,
                    new Data(
                            ints, // copied by Data constructor
                            variables.floats, // copied by Data constructor
                            variables.longs, // copied by Data constructor
                            variables.doubles, // copied by Data constructor
                            variables.objects, // copied by Data constructor
                            variables.continuationIndexes // copied by Data constructor
                    ),
                    operands);
        }

        /**
         * Helper to copy this frame but with new float variables.
         * @param floats new floats
         * @return new frame
         * @throws NullPointerException if any argument is {@code null}
         */
        public Frame withFloatVariables(float[] floats) {
            return new Frame(
                    className,
                    methodId,
                    continuationPointId,
                    monitors,
                    new Data(
                            variables.ints, // copied by Data constructor
                            floats, // copied by Data constructor
                            variables.longs, // copied by Data constructor
                            variables.doubles, // copied by Data constructor
                            variables.objects, // copied by Data constructor
                            variables.continuationIndexes // copied by Data constructor
                    ),
                    operands);
        }

        /**
         * Helper to copy this frame but with new long variables.
         * @param longs new longs
         * @return new frame
         * @throws NullPointerException if any argument is {@code null}
         */
        public Frame withLongVariables(long[] longs) {
            return new Frame(
                    className,
                    methodId,
                    continuationPointId,
                    monitors,
                    new Data(
                            variables.ints, // copied by Data constructor
                            variables.floats, // copied by Data constructor
                            longs, // copied by Data constructor
                            variables.doubles, // copied by Data constructor
                            variables.objects, // copied by Data constructor
                            variables.continuationIndexes // copied by Data constructor
                    ),
                    operands);
        }

        /**
         * Helper to copy this frame but with new double variables.
         * @param doubles new doubles
         * @return new frame
         * @throws NullPointerException if any argument is {@code null}
         */
        public Frame withDoubleVariables(double[] doubles) {
            return new Frame(
                    className,
                    methodId,
                    continuationPointId,
                    monitors,
                    new Data(
                            variables.ints, // copied by Data constructor
                            variables.floats, // copied by Data constructor
                            variables.longs, // copied by Data constructor
                            doubles, // copied by Data constructor
                            variables.objects, // copied by Data constructor
                            variables.continuationIndexes // copied by Data constructor
                    ),
                    operands);
        }

        /**
         * Helper to copy this frame but with new object variables.
         * @param objects new objects
         * @return new frame
         * @throws NullPointerException if any argument is {@code null}
         */
        public Frame withObjectVariables(Object[] objects) {
            return new Frame(
                    className,
                    methodId,
                    continuationPointId,
                    monitors,
                    new Data(
                            variables.ints, // copied by Data constructor
                            variables.floats, // copied by Data constructor
                            variables.longs, // copied by Data constructor
                            variables.doubles, // copied by Data constructor
                            objects, // copied by Data constructor
                            variables.continuationIndexes // copied by Data constructor
                    ),
                    operands);
        }

        /**
         * Helper to copy this frame but with new variable continuation indexes.
         * @param continuationIndexes new continuation indexes
         * @return new frame
         * @throws NullPointerException if any argument is {@code null}
         * @throws IllegalArgumentException if {@code continuationIndexes} points to out of bounds indexes within {@code objects} or if
         * if {@code continuationIndexes} points to non-null indexes within {@code objects}
         */
        public Frame withContinuationIndexVariables(int[] continuationIndexes) {
            return new Frame(
                    className,
                    methodId,
                    continuationPointId,
                    monitors,
                    new Data(
                            variables.ints, // copied by Data constructor
                            variables.floats, // copied by Data constructor
                            variables.longs, // copied by Data constructor
                            variables.doubles, // copied by Data constructor
                            variables.objects, // copied by Data constructor
                            continuationIndexes // copied by Data constructor
                    ),
                    operands);
        }

        /**
         * Helper to copy this frame but with potentially new operands.
         * @param ints new ints (or {@code null} if should be kept the same)
         * @param floats new floats (or {@code null} if should be kept the same)
         * @param longs new longs (or {@code null} if should be kept the same)
         * @param doubles new doubles (or {@code null} if should be kept the same)
         * @param objects new objects (or {@code null} if should be kept the same)
         * @param continuationIndexes new continuation indexes (or {@code null} if should be kept the same)
         * @return new frame
         * @throws IllegalArgumentException if {@code continuationIndexes} points to out of bounds indexes within {@code objects} or if
         * if {@code continuationIndexes} points to non-null indexes within {@code objects}
         */
        public Frame withOperands(
                int[] ints,
                float[] floats,
                long[] longs,
                double[] doubles,
                Object[] objects,
                int[] continuationIndexes) {
            return new Frame(
                    className,
                    methodId,
                    continuationPointId,
                    monitors,
                    variables,
                    new Data(
                            ints == null ? operands.ints : ints, // copied by Data constructor
                            floats == null ? operands.floats : floats, // copied by Data constructor
                            longs == null ? operands.longs : longs, // copied by Data constructor
                            doubles == null ? operands.doubles : doubles, // copied by Data constructor
                            objects == null ? operands.objects : objects, // copied by Data constructor
                            continuationIndexes == null ? operands.continuationIndexes : continuationIndexes // copied by Data constructor
                    ));
        }

        /**
         * Helper to copy this frame but with new int operands.
         * @param ints new ints
         * @return new frame
         * @throws NullPointerException if any argument is {@code null}
         */
        public Frame withIntOperands(int[] ints) {
            return new Frame(
                    className,
                    methodId,
                    continuationPointId,
                    monitors,
                    variables,
                    new Data(
                            ints, // copied by Data constructor
                            operands.floats, // copied by Data constructor
                            operands.longs, // copied by Data constructor
                            operands.doubles, // copied by Data constructor
                            operands.objects, // copied by Data constructor
                            operands.continuationIndexes // copied by Data constructor
                    )
            );
        }

        /**
         * Helper to copy this frame but with new float operands.
         * @param floats new floats
         * @return new frame
         * @throws NullPointerException if any argument is {@code null}
         */
        public Frame withFloatOperands(float[] floats) {
            return new Frame(
                    className,
                    methodId,
                    continuationPointId,
                    monitors,
                    variables,
                    new Data(
                            operands.ints, // copied by Data constructor
                            floats, // copied by Data constructor
                            operands.longs, // copied by Data constructor
                            operands.doubles, // copied by Data constructor
                            operands.objects, // copied by Data constructor
                            operands.continuationIndexes // copied by Data constructor
                    )
            );
        }

        /**
         * Helper to copy this frame but with new long operands.
         * @param longs new longs
         * @return new frame
         * @throws NullPointerException if any argument is {@code null}
         */
        public Frame withLongOperands(long[] longs) {
            return new Frame(
                    className,
                    methodId,
                    continuationPointId,
                    monitors,
                    variables,
                    new Data(
                            operands.ints, // copied by Data constructor
                            operands.floats, // copied by Data constructor
                            longs, // copied by Data constructor
                            operands.doubles, // copied by Data constructor
                            operands.objects, // copied by Data constructor
                            operands.continuationIndexes // copied by Data constructor
                    )
            );
        }

        /**
         * Helper to copy this frame but with new double operands.
         * @param doubles new doubles
         * @return new frame
         * @throws NullPointerException if any argument is {@code null}
         */
        public Frame withDoubleOperands(double[] doubles) {
            return new Frame(
                    className,
                    methodId,
                    continuationPointId,
                    monitors,
                    variables,
                    new Data(
                            operands.ints, // copied by Data constructor
                            operands.floats, // copied by Data constructor
                            operands.longs, // copied by Data constructor
                            doubles, // copied by Data constructor
                            operands.objects, // copied by Data constructor
                            operands.continuationIndexes // copied by Data constructor
                    )
            );
        }

        /**
         * Helper to copy this frame but with new object operands.
         * @param objects new objects
         * @return new frame
         * @throws NullPointerException if any argument is {@code null}
         */
        public Frame withObjectOperands(Object[] objects) {
            return new Frame(
                    className,
                    methodId,
                    continuationPointId,
                    monitors,
                    variables,
                    new Data(
                            operands.ints, // copied by Data constructor
                            operands.floats, // copied by Data constructor
                            operands.longs, // copied by Data constructor
                            operands.doubles, // copied by Data constructor
                            objects, // copied by Data constructor
                            operands.continuationIndexes // copied by Data constructor
                    )
            );
        }

        /**
         * Helper to copy this frame but with new variable continuation indexes.
         * @param continuationIndexes new continuation indexes
         * @return new frame
         * @throws NullPointerException if any argument is {@code null}
         * @throws IllegalArgumentException if {@code continuationIndexes} points to out of bounds indexes within {@code objects} or if
         * if {@code continuationIndexes} points to non-null indexes within {@code objects}
         */
        public Frame withContinuationIndexOperands(int[] continuationIndexes) {
            return new Frame(
                    className,
                    methodId,
                    continuationPointId,
                    monitors,
                    variables,
                    new Data(
                            operands.ints, // copied by Data constructor
                            operands.floats, // copied by Data constructor
                            operands.longs, // copied by Data constructor
                            operands.doubles, // copied by Data constructor
                            operands.objects, // copied by Data constructor
                            continuationIndexes // copied by Data constructor
                    )
            );
        }

        /**
         * Helper to copy this frame but with a potentially new new class name / method ID / continuation point ID.
         * @param className new class name (or {@code null} if should be kept the same)
         * @param methodId new method ID (or {@code null} if should be kept the same)
         * @param continuationPointId new continuation point ID (or {@code null} if should be kept the same)
         * @return new frame
         * @throws IllegalArgumentException if {@code continuationPointId < 0}
         */
        public Frame withKey(String className, Integer methodId, Integer continuationPointId) {
            return new Frame(
                    className == null ? this.className : className,
                    methodId == null ? this.methodId : methodId.intValue(),
                    continuationPointId == null ? this.continuationPointId : continuationPointId.intValue(),
                    monitors,
                    variables,
                    operands);
        }

        /**
         * Helper to copy this frame but with a new method ID.
         * @param className new class name
         * @return new frame
         * @throws NullPointerException if any argument is {@code null}
         */
        public Frame withClassname(String className) {
            return new Frame(
                    className,
                    methodId,
                    continuationPointId,
                    monitors,
                    variables,
                    operands);
        }

        /**
         * Helper to copy this frame but with a new method ID.
         * @param methodId new method ID
         * @return new frame
         */
        public Frame withMethodId(int methodId) {
            return new Frame(
                    className,
                    methodId,
                    continuationPointId,
                    monitors,
                    variables,
                    operands);
        }

        /**
         * Helper to copy this frame but with a new continuation point ID.
         * @param continuationPointId new continuation point ID
         * @return new frame
         * @throws IllegalArgumentException if {@code continuationPointId < 0}
         */
        public Frame withContinuationPointId(int continuationPointId) {
            return new Frame(
                    className,
                    methodId,
                    continuationPointId,
                    monitors,
                    variables,
                    operands);
        }

        void validateState() {
            if (monitors == null || operands == null || variables == null) {
                throw new IllegalStateException("Bad state");
            }

            if (continuationPointId < 0) {
                throw new IllegalStateException("Bad state");
            }

            for (int i = 0; i < monitors.length; i++) {
                if (monitors[i] == null) {
                    throw new IllegalStateException("Bad state");
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
        private static final long serialVersionUID = 6L;
        
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
            return (int[]) ints.clone();
        }

        /**
         * Get float values.
         * @return float values
         */
        public float[] getFloats() {
            return (float[]) floats.clone();
        }

        /**
         * Get long values.
         * @return long values
         */
        public long[] getLongs() {
            return (long[]) longs.clone();
        }

        /**
         * Get double values.
         * @return double values
         */
        public double[] getDoubles() {
            return (double[]) doubles.clone();
        }

        /**
         * Get object values.
         * @return object values
         */
        public Object[] getObjects() {
            return (Object[]) objects.clone();
        }

        /**
         * Get positions within objects array where the object pointed to the original {@link Continuation} for the coroutine.
         * @return positions within {@link #getObjects() } that point to the original {@link Continuation}
         */
        public int[] getContinuationIndexes() {
            return (int[]) continuationIndexes.clone();
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









    /**
     * Frame update point.
     * <p>
     * What's the difference between {@link FrameUpdatePoint} and {@link FrameInterceptPoint}? {@link FrameUpdatePoint} requires that the
     * modifier change at least one of the following properties of the frame: class name, method id, or continuation point id.
     * {@link FrameInterceptPoint} on the other hand requires that these properties remain the same.
     */
    public static final class FrameUpdatePoint {

        private final String className;
        private final int methodId;
        private final int continuationPointId;
        private final FrameModifier frameModifier;

        /**
         * Constructs a {@link FrameUpdatePoint} object.
         * @param className class name for the continuation point
         * @param methodId method id (used to identify method)
         * @param continuationPointId continuation point ID
         * @param frameModifier logic to modify the frame's contents to the new version
         * @throws NullPointerException if any argument is {@code null}
         * @throws IllegalArgumentException if {@code continuationPointId < 0}, or if {@code oldMethodId == @code newMethodId}
         */
        public FrameUpdatePoint(String className, int methodId, int continuationPointId, FrameModifier frameModifier) {
            if (frameModifier == null) {
                throw new NullPointerException();
            }
            if (continuationPointId < 0) {
                throw new IllegalArgumentException("Negative continuation point");
            }

            this.className = className;
            this.methodId = methodId;
            this.continuationPointId = continuationPointId;
            this.frameModifier = frameModifier;
        }

        FrameUpdatePointKey toKey() {
            return new FrameUpdatePointKey(className, methodId, continuationPointId);
        }
        
        FrameUpdatePointValue toValue() {
            return new FrameUpdatePointValue(frameModifier);
        }

        //CHECKSTYLE.OFF:JavadocMethod - Requires @Override annotation to work, but this is designed for Java 1.4 (no annotations support)
        public String toString() {
            return "FrameUpdatePoint{" + "className=" + className + ", methodId=" + methodId
                    + ", continuationPointId=" + continuationPointId + ", frameModifier=" + frameModifier + '}';
        }
        //CHECKSTYLE.ON:JavadocMethod

    }

    /**
     * Frame intercept point.
     * <p>
     * What's the difference between {@link FrameUpdatePoint} and {@link FrameInterceptPoint}? {@link FrameUpdatePoint} requires that the
     * modifier change at least one of the following properties of the frame: class name, method id, or continuation point id.
     * {@link FrameInterceptPoint} on the other hand requires that these properties remain the same.
     */
    public static final class FrameInterceptPoint {

        private final String className;
        private final int methodId;
        private final int continuationPointId;
        private final FrameModifier frameModifier;

        /**
         * Constructs a {@link FrameInterceptPoint} object.
         * @param className class name for the continuation point
         * @param methodId method id (used to identify method)
         * @param continuationPointId continuation point ID
         * @param frameModifier logic to modify the frame's contents to the new version
         * @throws NullPointerException if any argument is {@code null}
         * @throws IllegalArgumentException if {@code continuationPointId < 0}, or if {@code oldMethodId == @code newMethodId}
         */
        public FrameInterceptPoint(String className, int methodId, int continuationPointId, FrameModifier frameModifier) {
            if (frameModifier == null) {
                throw new NullPointerException();
            }
            if (continuationPointId < 0) {
                throw new IllegalArgumentException("Negative continuation point");
            }

            this.className = className;
            this.methodId = methodId;
            this.continuationPointId = continuationPointId;
            this.frameModifier = frameModifier;
        }

        FrameUpdatePointKey toKey() {
            return new FrameUpdatePointKey(className, methodId, continuationPointId);
        }
        
        FrameUpdatePointValue toValue() {
            return new FrameUpdatePointValue(frameModifier);
        }

        //CHECKSTYLE.OFF:JavadocMethod - Requires @Override annotation to work, but this is designed for Java 1.4 (no annotations support)
        public String toString() {
            return "FrameInterceptPoint{" + "className=" + className + ", methodId=" + methodId + ", continuationPointId="
                    + continuationPointId + ", frameModifier=" + frameModifier + '}';
        }
        //CHECKSTYLE.ON:JavadocMethod
    }

    /**
     * Frame modifier.
     */
    public interface FrameModifier {
        /**
         * Identifies that frame is being loaded.
         */
        int READ = 0;
        
        /**
         * Identifies that frame is being saved.
         */
        int WRITE = 1;

        /**
         * Called when a frame needs to be modified.
         * @param frame frame to modify
         * @param mode either {@link #READ} or {@link #WRITE}
         * @throws NullPointerException if any argument is {@code null}
         * @throws IllegalArgumentException if {@code mode != LOAD && mode != SAVE}
         * @return modified frame (copy)
         */
        Frame modifyFrame(Frame frame, int mode);
    }
}
