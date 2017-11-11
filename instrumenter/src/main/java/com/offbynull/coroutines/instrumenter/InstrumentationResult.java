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

import java.util.Arrays;
import org.apache.commons.lang3.Validate;

/**
 * Instrumentation results.
 * @author Kasra Faghihi
 */
public final class InstrumentationResult {
    private final byte[] instrumentedClass;
    private final String instrumentationDetails;

    InstrumentationResult(byte[] instrumentedClass, String instrumentationDetails) {
        Validate.notNull(instrumentedClass);
        // instrumentedInformation can be null
        this.instrumentedClass = Arrays.copyOf(instrumentedClass, instrumentedClass.length);
        this.instrumentationDetails = instrumentationDetails;
    }

    /**
     * Get instrumented class bytecode.
     * @return instrumented class bytecode
     */
    public byte[] getInstrumentedClass() {
        return Arrays.copyOf(instrumentedClass, instrumentedClass.length);
    }

    /**
     * Get human-readable instrumented class information.
     * @return instrumented class information ({@code null} if nothing in the class was instrumented)
     */
    public String getInstrumentationDetails() {
        return instrumentationDetails;
    }
}
