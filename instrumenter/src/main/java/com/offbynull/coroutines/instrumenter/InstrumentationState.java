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

import com.offbynull.coroutines.instrumenter.asm.ClassInformationRepository;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.tree.MethodNode;

final class InstrumentationState {
    private final InstrumentationSettings instrumentationSettings;
    private final ClassInformationRepository classInformationRepository;
    
    private final Map<MethodNode, MethodAttributes> methodAttributes;
    private final Map<String, byte[]> extraFiles;

    private ControlFlag stop;

    InstrumentationState(InstrumentationSettings instrumentationSettings, ClassInformationRepository classInformationRepository) {
        Validate.notNull(instrumentationSettings);
        Validate.notNull(classInformationRepository);
        this.instrumentationSettings = instrumentationSettings;
        this.classInformationRepository = classInformationRepository;
        
        this.methodAttributes = new HashMap<>();
        this.extraFiles = new HashMap<>();
        
        this.stop = ControlFlag.CONTINUE_INSTRUMENT;
    }

    InstrumentationSettings instrumentationSettings() {
        return instrumentationSettings;
    }

    ClassInformationRepository classInformationRepository() {
        return classInformationRepository;
    }

    Map<MethodNode, MethodAttributes> methodAttributes() {
        return methodAttributes;
    }

    Map<String, byte[]> extraFiles() {
        return extraFiles;
    }

    void control(ControlFlag control) {
        Validate.notNull(control);
        this.stop = control;
    }

    ControlFlag control() {
        return stop;
    }

    enum ControlFlag {
        CONTINUE_INSTRUMENT, //Continue passing forward to further instrumenters.
        NO_INSTRUMENT //Stop passing forward to further instrumenters -- it has been determined that this class must not be instrumented.
    }
}
