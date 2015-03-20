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
package com.offbynull.coroutines.user;

/**
 * Do not use -- for internal use only.
 * <p>
 * A marker interface that, if implemented by a class, means that the class has already been instrumented.
 * @author Kasra Faghihi
 */
public interface Instrumented {
    // The other alternative to using a marker interface was to do something like the following at the beginning of a method to identify it
    // as being instrumented...
    //
    // LDC "THIS METHOD HAS BEEN SUCCESSFULLY INSTRUMENTED TO USE JAVA COROUTINES"
    // POP
    //
    // The issue with this is that it may have the potential to be mangled by other programs that instrument the class + it wastes cycles
    // putting a constant on the stack and then popping it off again.
}
