/*
 * Copyright (c) 2016, Kasra Faghihi, All rights reserved.
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

/**
 * Memory allocator used for saving frames.
 * @author Kasra Faghihi
 */
public interface FrameAllocator {
    
    /**
     * Allocate int array.
     * @param size size of array
     * @return int array of at least {@code size} (may return {@code null} if {@code size} is 0)
     * @throws IllegalArgumentException if {@code size} is negative
     */
    int[] allocateIntArray(int size);

    /**
     * Allocate long array.
     * @param size size of array
     * @return long array of at least {@code size} (may return {@code null} if {@code size} is 0)
     * @throws IllegalArgumentException if {@code size} is negative
     */
    long[] allocateLongArray(int size);

    /**
     * Allocate float array.
     * @param size size of array
     * @return float array of at least {@code size} (may return {@code null} if {@code size} is 0)
     * @throws IllegalArgumentException if {@code size} is negative
     */
    float[] allocateFloatArray(int size);

    /**
     * Allocate double array.
     * @param size size of array
     * @return double array of at least {@code size} (may return {@code null} if {@code size} is 0)
     * @throws IllegalArgumentException if {@code size} is negative
     */
    double[] allocateDoubleArray(int size);

    /**
     * Allocate Object array.
     * @param size size of array
     * @return Object array of at least {@code size} (may return {@code null} if {@code size} is 0)
     * @throws IllegalArgumentException if {@code size} is negative
     */
    Object[] allocateObjectArray(int size);
    
    /**
     * Release int array.
     * @param arr int array (may be {@code null} -- ignore if it is)
     */
    void releaseIntArray(int[] arr);

    /**
     * Release long array.
     * @param arr long array (may be {@code null} -- ignore if it is)
     */
    void releaseLongArray(long[] arr);
    
    /**
     * Release float array.
     * @param arr float array (may be {@code null} -- ignore if it is)
     */
    void releaseFloatArray(float[] arr);
    
    /**
     * Release double array.
     * @param arr double array (may be {@code null} -- ignore if it is)
     */
    void releaseDoubleArray(double[] arr);
    
    /**
     * Release Object array.
     * @param arr Object array (may be {@code null} -- ignore if it is)
     */
    void releaseObjectArray(Object[] arr);
    
    /**
     * Roll back the allocation/releases performed. Arrays allocated/released must be put back where they came from.
     */
    void rollback();
    
    /**
     * Commit the allocation/releases performed.
     */
    void commit();
}
