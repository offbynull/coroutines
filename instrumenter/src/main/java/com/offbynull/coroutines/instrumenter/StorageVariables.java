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

import com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Type;

final class StorageVariables {
    private final Variable intStorageVar;
    private final Variable longStorageVar;
    private final Variable floatStorageVar;
    private final Variable doubleStorageVar;
    private final Variable objectStorageVar;
    
    StorageVariables(
            Variable intStorageVar,
            Variable longStorageVar,
            Variable floatStorageVar,
            Variable doubleStorageVar,
            Variable objectStorageVar) {
        // storage vars CAN BE NULL -- if they weren't created it means it was determined that it wasn't required
        Validate.isTrue(intStorageVar == null || intStorageVar.getType().equals(Type.getType(int[].class)));
        Validate.isTrue(longStorageVar == null || longStorageVar.getType().equals(Type.getType(long[].class)));
        Validate.isTrue(floatStorageVar == null || floatStorageVar.getType().equals(Type.getType(float[].class)));
        Validate.isTrue(doubleStorageVar == null || doubleStorageVar.getType().equals(Type.getType(double[].class)));
        Validate.isTrue(objectStorageVar == null || objectStorageVar.getType().equals(Type.getType(Object[].class)));
        
        this.intStorageVar = intStorageVar;
        this.longStorageVar = longStorageVar;
        this.floatStorageVar = floatStorageVar;
        this.doubleStorageVar = doubleStorageVar;
        this.objectStorageVar = objectStorageVar;
    }

    public Variable getIntStorageVar() {
        return intStorageVar;
    }

    public Variable getLongStorageVar() {
        return longStorageVar;
    }

    public Variable getFloatStorageVar() {
        return floatStorageVar;
    }

    public Variable getDoubleStorageVar() {
        return doubleStorageVar;
    }

    public Variable getObjectStorageVar() {
        return objectStorageVar;
    }
}
