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

import static com.offbynull.coroutines.instrumenter.InstrumentationState.ControlFlag.NO_INSTRUMENT;
import com.offbynull.coroutines.instrumenter.asm.ClassInformationRepository;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.Validate;

// Analyze what has been marked for instrumentation (more methods may be filtered out here).
final class AnalyzeInstrumentationPass implements InstrumentationPass {

    @Override
    public void pass(ClassNode classNode, InstrumentationState state) {
        Validate.notNull(classNode);
        Validate.notNull(state);



        // Methods to instrument should be identified, but no method attributes should be assigned yet -- this is what this pass does. There
        // must be atleast 1 method to exist (previous pass should have stopped the instrumentation process if not)
        Validate.validState(!state.methodAttributes().isEmpty());
        Validate.validState(state.methodAttributes().keySet().stream().allMatch(x -> x != null));
        Validate.validState(state.methodAttributes().values().stream().allMatch(x -> x == null));

        // Sanity check to make sure that we're only dealing with methodnodes in the classnode -- this should never trigger unless previous
        // passes mess up
        Validate.validState(classNode.methods.containsAll(state.methodAttributes().keySet()));



        // Analyze each method that needs to be instrumented
        ClassInformationRepository classRepo = state.classInformationRepository();
        InstrumentationSettings settings = state.instrumentationSettings();

        MethodAnalyzer analyzer = new MethodAnalyzer(classRepo);

        Set<MethodNode> methodNodes = new HashSet<>(state.methodAttributes().keySet()); // create a copy and iterate of that,
                                                                                       // otherwise we are modifying and iterating
                                                                                       // over the collection at the same time
        for (MethodNode methodNode : methodNodes) {
            MethodAttributes methodAttrs = analyzer.analyze(classNode, methodNode, settings);
            
            // If methodAttrs is null, it means that the analyzer determined that the method doesn't need to be instrumented.
            if (methodAttrs != null) {
                state.methodAttributes().put(methodNode, methodAttrs); // replace the entry (replaces the null value with methodAttrs)
            } else {
                state.methodAttributes().remove(methodNode);           // remove the placeholder
            }
        }


        // Has everything been removed? The class doesn't need to be instrumented at all.
        if (state.methodAttributes().isEmpty()) {
            state.control(NO_INSTRUMENT);
        }
    }
}
