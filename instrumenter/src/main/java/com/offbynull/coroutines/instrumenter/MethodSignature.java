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

import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Type;

final class MethodSignature {
    private final String className;
    private final String methodName;
    private final Type methodDescriptor;

    MethodSignature(String className, String methodName, Type methodDescriptor) {
        Validate.notNull(className);
        Validate.notNull(methodName);
        Validate.notNull(methodDescriptor);
        Validate.isTrue(methodDescriptor.getSort() == Type.METHOD);
        
        this.className = className;
        this.methodName = methodName;
        this.methodDescriptor = methodDescriptor;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public Type getMethodDescriptor() {
        return methodDescriptor;
    }
    
    public Type getReturnType() {
        return methodDescriptor.getReturnType();
    }
}
