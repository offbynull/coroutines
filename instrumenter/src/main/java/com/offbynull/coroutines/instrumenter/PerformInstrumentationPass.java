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

import static com.offbynull.coroutines.instrumenter.InternalFields.INSTRUMENTED_MARKER_FIELD_ACCESS;
import static com.offbynull.coroutines.instrumenter.InternalFields.INSTRUMENTED_MARKER_FIELD_NAME;
import static com.offbynull.coroutines.instrumenter.InternalFields.INSTRUMENTED_MARKER_FIELD_TYPE;
import static com.offbynull.coroutines.instrumenter.InternalFields.INSTRUMENTED_MARKER_FIELD_VALUE;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import java.util.Map.Entry;
import org.apache.commons.lang3.Validate;


// Main insturmentation pass. This is the instrumenter that re-works your logic such that your coroutines work.
final class PerformInstrumentationPass implements InstrumentationPass {

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


        // Instrument the methods based on the analysis we did in previous passes
        MethodInstrumenter instrumenter = new MethodInstrumenter();
        for (Entry<MethodNode, MethodAttributes> method : state.methodAttributes().entrySet()) {            
            MethodNode methodNode = method.getKey();
            MethodAttributes methodAttrs = method.getValue();

            // Instrument
            instrumenter.instrument(classNode, methodNode, methodAttrs);
        }


        // Add the "Instrumented" marker field to this class so if we ever come back to it, we can skip it
        FieldNode instrumentedMarkerField = new FieldNode(
                INSTRUMENTED_MARKER_FIELD_ACCESS,
                INSTRUMENTED_MARKER_FIELD_NAME,
                INSTRUMENTED_MARKER_FIELD_TYPE.getDescriptor(),
                null,
                INSTRUMENTED_MARKER_FIELD_VALUE);
        classNode.fields.add(instrumentedMarkerField);
    }
}
