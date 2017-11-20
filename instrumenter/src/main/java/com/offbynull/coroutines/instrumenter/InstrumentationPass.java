/*
 * Copyright (c) 2017, Kasra Faghihi, All rights reserved.
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

import org.objectweb.asm.tree.ClassNode;

/**
 * Instrumentation pass.
 * @author Kasra Faghihi
 */
interface InstrumentationPass {
    
    /**
     * Performs the instrumentation pass.
     * @param classNode class node being operated on
     * @param state state shared between instrumentation passes
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if could not continue instrumentation pass based on inputs
     */
    void pass(ClassNode classNode, InstrumentationState state);
}
