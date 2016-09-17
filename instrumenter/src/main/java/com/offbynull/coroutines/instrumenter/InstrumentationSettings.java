/*
 * Copyright (c) 2016, Kasra Faghihi, All rights reserved.
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

import com.offbynull.coroutines.instrumenter.generators.DebugGenerators.MarkerType;
import org.apache.commons.lang3.Validate;

/**
 * Instrumentation settings.
 * @author Kasra Faghihi
 */
public final class InstrumentationSettings {
    private final MarkerType markerType;
    private final boolean debugMode;

    /**
     * Constructs a {@link InstrumentationSettings} object.
     * @param markerType marker type
     * @param debugMode debug mode
     * @throws NullPointerException if any argument is {@code null}
     */
    public InstrumentationSettings(MarkerType markerType, boolean debugMode) {
        Validate.notNull(markerType);
        this.markerType = markerType;
        this.debugMode = debugMode;
    }

    /**
     * Get marker type. Depending on the marker type used, markers will be added to the instrumented code that explains what each portion of
     * the instrumented code is doing. This is useful for debugging the instrumentation logic (if the instrumented code is bugged).
     * @return marker type
     */
    public MarkerType getMarkerType() {
        return markerType;
    }

    /**
     * Get debug mode. Debug mode adds extra instrumentation code to the class such that the instrumented method's state is viewable when
     * you run the instrumented code through a debugger (e.g. the debugger in Netbeans/Eclipse/IntelliJ).
     * @return debug mode
     */
    public boolean isDebugMode() {
        return debugMode;
    }
}
