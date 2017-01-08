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
package com.offbynull.coroutines.gradleplugin;

import org.apache.commons.lang3.Validate;

/**
 * Coroutines Gradle plugin configuration.
 * @author Kasra Faghihi
 */
public final class CoroutinesPluginConfiguration {

    private String jdkLibsDirectory;
    private String markerType;
    private boolean debugMode;

    /**
     * Constructs a {@link ConroutinesPluginConfiguration} object.
     */
    public CoroutinesPluginConfiguration() {
        jdkLibsDirectory = System.getProperty("java.home") + "/lib";
        markerType = "NONE";
        debugMode = false;
    }

    /**
     * Get JDK library directory.
     *
     * @return JDK library directory
     */
    public String getJdkLibsDirectory() {
        return jdkLibsDirectory;
    }

    /**
     * Set JDK library directory.
     *
     * @param jdkLibsDirectory JDK library directory
     * @throws NullPointerException if any argument is {@code null}
     */
    public void setJdkLibsDirectory(String jdkLibsDirectory) {
        Validate.notNull(jdkLibsDirectory);
        this.jdkLibsDirectory = jdkLibsDirectory;
    }

    /**
     * Get marker type.
     *
     * @return marker type
     */
    public String getMarkerType() {
        return markerType;
    }

    /**
     * Set marker type.
     *
     * @param markerType marker type
     * @throws NullPointerException if any argument is {@code null}
     */
    public void setMarkerType(String markerType) {
        Validate.notNull(markerType);
        this.markerType = markerType;
    }

    /**
     * Get debug mode.
     *
     * @return debug mode
     */
    public boolean isDebugMode() {
        return debugMode;
    }

    /**
     * Set debug mode.
     *
     * @param debugMode debug mode
     */
    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }
    
}
