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

import static com.offbynull.coroutines.instrumenter.InternalFields.INSTRUMENTED_SERIALIZEUID_FIELD_ACCESS;
import static com.offbynull.coroutines.instrumenter.InternalFields.INSTRUMENTED_SERIALIZEUID_FIELD_NAME;
import static com.offbynull.coroutines.instrumenter.InternalFields.INSTRUMENTED_SERIALIZEUID_FIELD_TYPE;
import static com.offbynull.coroutines.instrumenter.InternalFields.INSTRUMENTED_SERIALIZEUID_FIELD_VALUE;
import java.io.Serializable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.tree.FieldNode;

// Automatically transforms classes passed in such that they implement {@link Serializable} and assign a {@code serialVersionUID} of 0.
// 
// This is vital for the serialization feature (if the user uses the default serializer). If your class doesn't extend {@link Serializable},
// you'll get an exception when serializing it. Also, if you don't hardcode a {@code serialVersionUID} onto the class, any change to the
// class will cause deserialization to fail. These 2 properties are features of Java's built-in serialization mechanism (used by the default
// serializer).
final class AutoSerializableInstrumentationPass implements InstrumentationPass {

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

        
        // Should we skip this?
        if (!state.instrumentationSettings().isAutoSerializable()) {
            return;
        }

        
        // Skip if interface
        if ((classNode.access & Opcodes.ACC_INTERFACE) == Opcodes.ACC_INTERFACE) {
            throw new IllegalArgumentException("Auto-serializing an interface not allowed");
        }
        
        
        //Add the field in
        Type serializableType = Type.getType(Serializable.class);
        String serializableTypeStr = serializableType.getInternalName();
        
        if (!classNode.interfaces.contains(serializableTypeStr)) {
            classNode.interfaces.add(serializableTypeStr);
        }

        if (!classNode.fields.stream().anyMatch(fn -> INSTRUMENTED_SERIALIZEUID_FIELD_NAME.equals(fn.name))) {
            FieldNode serialVersionUIDFieldNode = new FieldNode(
                    INSTRUMENTED_SERIALIZEUID_FIELD_ACCESS,
                    INSTRUMENTED_SERIALIZEUID_FIELD_NAME,
                    INSTRUMENTED_SERIALIZEUID_FIELD_TYPE.getDescriptor(),
                    null,
                    INSTRUMENTED_SERIALIZEUID_FIELD_VALUE);
            classNode.fields.add(serialVersionUIDFieldNode);
        }
    }

}
