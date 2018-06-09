/*
 * Copyright (c) 2018, Kasra Faghihi, All rights reserved.
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
package com.offbynull.coroutines.instrumenter.asm;

import java.util.ArrayList;
import static java.util.Arrays.asList;
import org.apache.commons.collections4.list.UnmodifiableList;
import static org.apache.commons.collections4.list.UnmodifiableList.unmodifiableList;
import org.apache.commons.lang3.Validate;

/**
 * Combines multiple {@link ClassInformationRepository} objects into one.
 * @author Kasra Faghihi
 * @see ClassLoaderClassInformationRepository
 */
public final class CompositeClassInformationRepository implements ClassInformationRepository {
    private final UnmodifiableList<ClassInformationRepository> repos;

    /**
     * Constructs a {@link ClassLoaderClassInformationRepository} object.
     * @param repos class information repositories
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code repos} contains {@code null}
     */
    public CompositeClassInformationRepository(ClassInformationRepository... repos) {
        Validate.notNull(repos);
        Validate.noNullElements(repos);
        this.repos = (UnmodifiableList<ClassInformationRepository>) unmodifiableList(new ArrayList<>(asList(repos)));
    }
    
    @Override
    public ClassInformation getInformation(String internalClassName) {
        Validate.notNull(internalClassName);
        
        for (ClassInformationRepository repo : repos) {
            ClassInformation classInfo = repo.getInformation(internalClassName);
            if (classInfo != null) {
                return classInfo;
            }
        }

        return null;
    }
}
