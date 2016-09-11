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
package com.offbynull.coroutines.instrumenter;

final class StorageSizes {
    
    private final int intsSize;
    private final int longsSize;
    private final int floatsSize;
    private final int doublesSize;
    private final int objectsSize;

    StorageSizes(int intsSize, int longsSize, int floatsSize, int doublesSize, int objectsSize) {
        this.intsSize = intsSize;
        this.longsSize = longsSize;
        this.floatsSize = floatsSize;
        this.doublesSize = doublesSize;
        this.objectsSize = objectsSize;
    }

    public int getIntsSize() {
        return intsSize;
    }

    public int getLongsSize() {
        return longsSize;
    }

    public int getFloatsSize() {
        return floatsSize;
    }

    public int getDoublesSize() {
        return doublesSize;
    }

    public int getObjectsSize() {
        return objectsSize;
    }
    
}
