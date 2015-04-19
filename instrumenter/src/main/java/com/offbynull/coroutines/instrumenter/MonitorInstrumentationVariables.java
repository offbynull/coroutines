/*
 * Copyright (c) 2015, Kasra Faghihi, All rights reserved.
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

import com.offbynull.coroutines.instrumenter.asm.VariableTable;
import com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable;
import com.offbynull.coroutines.user.LockState;
import com.offbynull.coroutines.user.MethodState;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Type;

final class MonitorInstrumentationVariables {

    private final Variable methodStateVar;
    private final Variable tempObjectVar;
    private final Variable lockStateVar;
    private final Variable counterVar;
    private final Variable arrayLenVar;
    
    public MonitorInstrumentationVariables(VariableTable varTable, Variable methodStateVar, Variable tempObjectVar) {
        Validate.notNull(varTable);
        Validate.notNull(methodStateVar);
        Validate.notNull(tempObjectVar);
        Validate.isTrue(methodStateVar.getType().equals(Type.getType(MethodState.class)));
        Validate.isTrue(tempObjectVar.getType().equals(Type.getType(Object.class)));
        
        this.methodStateVar = methodStateVar;
        this.tempObjectVar = tempObjectVar;
        lockStateVar = varTable.acquireExtra(LockState.class);
        counterVar = varTable.acquireExtra(Type.INT_TYPE);
        arrayLenVar = varTable.acquireExtra(Type.INT_TYPE);
    }

    public Variable getMethodStateVar() {
        return methodStateVar;
    }

    public Variable getTempObjectVar() {
        return tempObjectVar;
    }

    public Variable getLockStateVar() {
        return lockStateVar;
    }

    public Variable getCounterVar() {
        return counterVar;
    }

    public Variable getArrayLenVar() {
        return arrayLenVar;
    }
    
}
