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

import com.offbynull.coroutines.instrumenter.generators.DebugGenerators.MarkerType;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections4.list.UnmodifiableList;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Type;

final class MethodProperties {
    private final String methodName;
    private final Type methodSignature;
    private final MarkerType debugMarkerType;

    private final UnmodifiableList<ContinuationPoint> continuationPoints;
    private final UnmodifiableList<SynchronizationPoint> synchPoints;
    
    private final CoreVariables coreVars;
    private final CacheVariables cacheVars;
    private final LockVariables lockVars;

    MethodProperties(
            String methodName,
            Type methodSignature,
            MarkerType debugMarkerType,
            List<ContinuationPoint> continuationPoints,
            List<SynchronizationPoint> synchPoints,
            CoreVariables coreVars,
            CacheVariables cacheVars,
            LockVariables lockVars) {
        Validate.notNull(methodName);
        Validate.notNull(methodSignature);
        Validate.notNull(debugMarkerType);
        Validate.notNull(continuationPoints);
        Validate.notNull(synchPoints);
        Validate.notNull(coreVars);
        Validate.notNull(cacheVars);
        Validate.notNull(lockVars);
        Validate.noNullElements(continuationPoints);
        Validate.noNullElements(synchPoints);
        Validate.isTrue(methodSignature.getSort() == Type.METHOD);

        this.methodName = methodName;
        this.methodSignature = methodSignature;
        this.debugMarkerType = debugMarkerType;
        this.continuationPoints =
                (UnmodifiableList<ContinuationPoint>) UnmodifiableList.unmodifiableList(new ArrayList<>(continuationPoints));
        this.synchPoints =
                (UnmodifiableList<SynchronizationPoint>) UnmodifiableList.unmodifiableList(new ArrayList<>(synchPoints));
        this.coreVars = coreVars;
        this.cacheVars = cacheVars;
        this.lockVars = lockVars;
    }

    public String getMethodName() {
        return methodName;
    }

    public Type getMethodSignature() {
        return methodSignature;
    }

    public Type getMethodReturnType() {
        return methodSignature.getReturnType();
    }

    public MarkerType getDebugMarkerType() {
        return debugMarkerType;
    }

    public UnmodifiableList<ContinuationPoint> getContinuationPoints() {
        return continuationPoints;
    }

    public UnmodifiableList<SynchronizationPoint> getSynchronizationPoints() {
        return synchPoints;
    }

    public CoreVariables getCoreVariables() {
        return coreVars;
    }

    public CacheVariables getCacheVariables() {
        return cacheVars;
    }

    public LockVariables getLockVariables() {
        return lockVars;
    }
}
