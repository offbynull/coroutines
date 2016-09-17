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

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections4.list.UnmodifiableList;
import org.apache.commons.lang3.Validate;

final class MethodAttributes {
    private final MethodSignature signature;
    private final InstrumentationSettings settings;

    private final UnmodifiableList<ContinuationPoint> continuationPoints;
    private final UnmodifiableList<SynchronizationPoint> synchPoints;
    
    private final CoreVariables coreVars;
    private final CacheVariables cacheVars;
    private final StorageContainerVariables storageContainerVars;
    private final StorageVariables localsStorageVars;
    private final StorageVariables stackStorageVars;
    private final LockVariables lockVars;

    MethodAttributes(
            MethodSignature signature,
            InstrumentationSettings settings,
            List<ContinuationPoint> continuationPoints,
            List<SynchronizationPoint> synchPoints,
            CoreVariables coreVars,
            CacheVariables cacheVars,
            StorageContainerVariables storageContainerVars,
            StorageVariables localsStorageVars,
            StorageVariables stackStorageVars,
            LockVariables lockVars) {
        Validate.notNull(signature);
        Validate.notNull(settings);
        Validate.notNull(continuationPoints);
        Validate.notNull(synchPoints);
        Validate.notNull(coreVars);
        Validate.notNull(cacheVars);
        Validate.notNull(storageContainerVars);
        Validate.notNull(localsStorageVars);
        Validate.notNull(stackStorageVars);
        Validate.notNull(lockVars);
        Validate.noNullElements(continuationPoints);
        Validate.noNullElements(synchPoints);

        this.signature = signature;
        this.settings = settings;
        this.continuationPoints =
                (UnmodifiableList<ContinuationPoint>) UnmodifiableList.unmodifiableList(new ArrayList<>(continuationPoints));
        this.synchPoints =
                (UnmodifiableList<SynchronizationPoint>) UnmodifiableList.unmodifiableList(new ArrayList<>(synchPoints));
        this.coreVars = coreVars;
        this.cacheVars = cacheVars;
        this.storageContainerVars = storageContainerVars;
        this.localsStorageVars = localsStorageVars;
        this.stackStorageVars = stackStorageVars;
        this.lockVars = lockVars;
    }

    public MethodSignature getSignature() {
        return signature;
    }

    public InstrumentationSettings getSettings() {
        return settings;
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

    public StorageContainerVariables getStorageContainerVariables() {
        return storageContainerVars;
    }

    public StorageVariables getLocalsStorageVariables() {
        return localsStorageVars;
    }

    public StorageVariables getStackStorageVariables() {
        return stackStorageVars;
    }

    public LockVariables getLockVariables() {
        return lockVars;
    }
}
