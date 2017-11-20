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
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.tree.MethodNode;

// Writes out serialization details as an extra file with the same as the class but a {@code .coroutinesinfo} extension.
final class SerializationPreInstrumentationPass implements InstrumentationPass {

    @Override
    public void pass(ClassNode classNode, InstrumentationState state) {
        Validate.notNull(classNode);
        Validate.notNull(state);


        // Methods attributes should be assigned at this point.
        Validate.validState(!state.methodAttributes().isEmpty());
        Validate.validState(state.methodAttributes().keySet().stream().allMatch(x -> x != null));
        Validate.validState(state.methodAttributes().values().stream().allMatch(x -> x != null));

        // Sanity check to make sure that we're only dealing with methodnodes in the classnode -- this should never trigger unless previous
        // passes mess up
        Validate.validState(classNode.methods.containsAll(state.methodAttributes().keySet()));


        // Generate for the .coroutinesinfo details for the methods we're about to instrument (MUST NOT BE INSTRUMETED AT THIS POINT)
        SerializationDetailer detailer = new SerializationDetailer();
        StringBuilder details = new StringBuilder();
        for (Map.Entry<MethodNode, MethodAttributes> method : state.methodAttributes().entrySet()) {            
            MethodNode methodNode = method.getKey();
            MethodAttributes methodAttrs = method.getValue();
            
            // Write serialization details before instrumenting
            detailer.detail(methodNode, methodAttrs, details);
        }
        
        
        // Add the serialization details for output
        state.extraFiles().put(
                getNameWithoutPackage(classNode.name) + ".coroutinesinfo",
                details.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String getNameWithoutPackage(String internalClassName) {
        Validate.notNull(internalClassName);

        int idx = internalClassName.lastIndexOf('/');
        if (idx == -1) {
            return internalClassName;
        }

        return internalClassName.substring(idx + 1);
    }
}
