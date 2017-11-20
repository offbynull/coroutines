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

import static com.offbynull.coroutines.instrumenter.InternalFields.INSTRUMENTED_METHODID_FIELD_ACCESS;
import static com.offbynull.coroutines.instrumenter.InternalFields.INSTRUMENTED_METHODID_FIELD_TYPE;
import static com.offbynull.coroutines.instrumenter.InternalFields.INSTRUMENTED_METHODID_FIELD_VALUE;
import static com.offbynull.coroutines.user.MethodState.getIdentifyingFieldName;
import org.objectweb.asm.tree.ClassNode;
import java.util.Map;
import org.apache.commons.collections4.list.UnmodifiableList;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

// Adds fields used by serializer/deserializer to identify which version of the method its working with.
final class SerializationPostInstrumentationPass implements InstrumentationPass {

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


        // Generate the fields needed by the serializer/deserializer
        for (Map.Entry<MethodNode, MethodAttributes> method : state.methodAttributes().entrySet()) {            
            MethodAttributes methodAttrs = method.getValue();
            
            UnmodifiableList<ContinuationPoint> continuationPoints =  methodAttrs.getContinuationPoints();
            
            // Shove in versioning info for the method as a fields on the class. 
            int methodId = methodAttrs.getSignature().getMethodId();
            for (int i = 0; i < continuationPoints.size(); i++) {
                int continuationPointId = i;
                FieldNode methodIdField = new FieldNode(
                        INSTRUMENTED_METHODID_FIELD_ACCESS,
                        getIdentifyingFieldName(methodId, continuationPointId),
                        INSTRUMENTED_METHODID_FIELD_TYPE.getDescriptor(),
                        null,
                        INSTRUMENTED_METHODID_FIELD_VALUE);
                classNode.fields.add(methodIdField);
            }
        }
    }
}
