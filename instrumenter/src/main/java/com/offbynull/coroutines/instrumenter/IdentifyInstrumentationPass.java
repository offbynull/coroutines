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

import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.findField;
import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.findMethodsWithParameter;
import static com.offbynull.coroutines.instrumenter.InstrumentationState.ControlFlag.NO_INSTRUMENT;
import static com.offbynull.coroutines.instrumenter.InternalFields.INSTRUMENTED_MARKER_FIELD_ACCESS;
import static com.offbynull.coroutines.instrumenter.InternalFields.INSTRUMENTED_MARKER_FIELD_NAME;
import static com.offbynull.coroutines.instrumenter.InternalFields.INSTRUMENTED_MARKER_FIELD_TYPE;
import static com.offbynull.coroutines.instrumenter.InternalFields.INSTRUMENTED_MARKER_FIELD_VALUE;
import com.offbynull.coroutines.user.Continuation;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.apache.commons.lang3.Validate;


// Identify methods that need to be instrumented
final class IdentifyInstrumentationPass implements InstrumentationPass {

    private static final Type CONTINUATION_CLASS_TYPE = Type.getType(Continuation.class);

    @Override
    public void pass(ClassNode classNode, InstrumentationState state) {
        Validate.notNull(classNode);
        Validate.notNull(state);
        
        Validate.validState(state.methodAttributes().isEmpty());
        
        // Is this class an interface? if so, skip this class
        if ((classNode.access & Opcodes.ACC_INTERFACE) == Opcodes.ACC_INTERFACE) {
            state.control(NO_INSTRUMENT);
            return;
        }

        // Has this class already been instrumented? if so, skip this class
        FieldNode instrumentedMarkerField = findField(classNode, INSTRUMENTED_MARKER_FIELD_NAME);
        if (instrumentedMarkerField != null) {
            if (INSTRUMENTED_MARKER_FIELD_ACCESS != instrumentedMarkerField.access) {
                throw new IllegalArgumentException("Instrumentation marker found with wrong access: " + instrumentedMarkerField.access);
            }
            if (!INSTRUMENTED_MARKER_FIELD_TYPE.getDescriptor().equals(instrumentedMarkerField.desc)) {
                throw new IllegalArgumentException("Instrumentation marker found with wrong type: " + instrumentedMarkerField.desc);
            }
            if (!INSTRUMENTED_MARKER_FIELD_VALUE.equals(instrumentedMarkerField.value)) {
                throw new IllegalArgumentException("Instrumentation marker found wrong value: " + instrumentedMarkerField.value);
            }

            state.control(NO_INSTRUMENT);
            return;
        }

        // Find methods that need to be instrumented. If none are found, skip this class;
        List<MethodNode> methodNodesToInstrument = findMethodsWithParameter(classNode.methods, CONTINUATION_CLASS_TYPE);
        if (methodNodesToInstrument.isEmpty()) {
            state.control(NO_INSTRUMENT);
            return;
        }

        // Add into internal data structure used for sharing info between passes
        methodNodesToInstrument.forEach(mn -> {
            MethodAttributes existing = state.methodAttributes().putIfAbsent(mn, null);
            Validate.validState(existing == null); // sanity check, will never happen
        });
    }
}
