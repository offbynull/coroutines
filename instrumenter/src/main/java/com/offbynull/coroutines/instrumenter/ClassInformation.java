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

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.Validate;

/**
 * Contains information about a class.
 * @author Kasra Faghihi
 */
public final class ClassInformation {
    private final String superClassName;
    private final List<String> interfaces;

    /**
     * Construct a {@link ClassInformation} object.
     * @param superClassName name of parent class (can be {@code null})
     * @param interfaces interface names
     * @throws NullPointerException if {@code interfaces} is {@code null} or contains {@code null}
     */
    public ClassInformation(String superClassName, List<String> interfaces) {
        Validate.notNull(interfaces);
        Validate.noNullElements(interfaces);
        
        this.superClassName = superClassName;
        this.interfaces = new ArrayList<>(interfaces);
    }

    /**
     * Get the parent class name.
     * @return parent class name (may be {@code null})
     */
    public String getSuperClassName() {
        return superClassName;
    }

    /**
     * Gets the implemented interfaces.
     * @return interfaces
     */
    public List<String> getInterfaces() {
        return new ArrayList<>(interfaces);
    }
    
}
