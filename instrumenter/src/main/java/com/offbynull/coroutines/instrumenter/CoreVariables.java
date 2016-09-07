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

final class CoreVariables {
    private final Variable continuationArgVar;
    private final Variable methodStateVar;
    
    private final Variable savedLocalsVar;
    private final Variable savedStackVar;
    private final Variable savedArgumentsVar;
    private final Variable savedPartialStackVar;
    
    CoreVariables(
            Variable continuationArgVar,
            Variable methodStateVar,
            Variable savedLocalsVar,
            Variable savedStackVar,
            Variable savedArgumentsVar,
            Variable savedPartialStackVar) {
        Validate.notNull(continuationArgVar);
        Validate.notNull(methodStateVar);
        Validate.notNull(savedLocalsVar);
        Validate.notNull(savedStackVar);
        Validate.notNull(savedArgumentsVar);
        Validate.notNull(savedPartialStackVar);
        
        this.continuationArgVar = continuationArgVar;
        this.methodStateVar = methodStateVar;
        this.savedLocalsVar = savedLocalsVar;
        this.savedStackVar = savedStackVar;
        this.savedArgumentsVar = savedArgumentsVar;
        this.savedPartialStackVar = savedPartialStackVar;
    }

    public Variable getContinuationArgVar() {
        return continuationArgVar;
    }

    public Variable getMethodStateVar() {
        return methodStateVar;
    }

    public Variable getSavedLocalsVar() {
        return savedLocalsVar;
    }

    public Variable getSavedStackVar() {
        return savedStackVar;
    }

    public Variable getSavedArgumentsVar() {
        return savedArgumentsVar;
    }

    public Variable getSavedPartialStackVar() {
        return savedPartialStackVar;
    }
}
