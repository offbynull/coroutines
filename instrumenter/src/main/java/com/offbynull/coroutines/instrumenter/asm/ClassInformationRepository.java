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

/**
 * Class information repository. Used by overridden ASM classes (e.g. {@link SimpleClassWriter}) to access information about classes
 * (e.g. used to derive the common super class between two classes).
 * @author Kasra Faghihi
 */
public interface ClassInformationRepository {

    /**
     * Get information for a class.
     * <p>
     * This method returns class information as if it were encountered in a class file. In a class file, if the class is an interface, then
     * its superclass is set to {@link Object}. Note that this is different from what {@link Class#getSuperclass() } returns when the class
     * represents an interface (it returns {@code null}).
     * @param internalClassName internal class name
     * @return information for that class, or {@code null} if not found
     */
    ClassInformation getInformation(String internalClassName);
    
}
