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
package com.offbynull.coroutines.instrumenter.asm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.Validate;

/**
 * Contains information about a class.
 * @author Kasra Faghihi
 */
public final class ClassInformation {
    private final String superClassName;
    private final List<String> interfaces;
    private final boolean interfaceMarker;

    /**
     * Construct a {@link ClassInformation} object.
     * @param superClassName name of parent class (can be {@code null})
     * @param interfaces interface names
     * @param interfaceMarker {@code true} if class is an interface, {@code false} otherwise
     * @throws NullPointerException if {@code interfaces} is {@code null} or contains {@code null}
     */
    public ClassInformation(String superClassName, List<String> interfaces, boolean interfaceMarker) {
        Validate.notNull(interfaces);
        Validate.noNullElements(interfaces);
        
        this.superClassName = superClassName;
        this.interfaces = new ArrayList<>(interfaces);
        this.interfaceMarker = interfaceMarker;
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

    /**
     * Whether or not this class is an interface.
     * @return {@code true} if this class is an interface, {@code false} otherwise
     */
    public boolean isInterface() {
        return interfaceMarker;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + Objects.hashCode(this.superClassName);
        hash = 47 * hash + Objects.hashCode(this.interfaces);
        hash = 47 * hash + (this.interfaceMarker ? 1 : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ClassInformation other = (ClassInformation) obj;
        if (!Objects.equals(this.superClassName, other.superClassName)) {
            return false;
        }
        if (!Objects.equals(this.interfaces, other.interfaces)) {
            return false;
        }
        if (this.interfaceMarker != other.interfaceMarker) {
            return false;
        }
        return true;
    }
    
}
