/*
 * Copyright (c) 2018, Kasra Faghihi, All rights reserved.
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

import com.offbynull.coroutines.instrumenter.InstrumentationSettings;
import com.offbynull.coroutines.instrumenter.Instrumenter;
import com.offbynull.coroutines.instrumenter.PluginHelper;
import com.offbynull.coroutines.instrumenter.generators.DebugGenerators.MarkerType;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static java.util.stream.Collectors.toList;
import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;

//CHECKSTYLE.OFF:DesignForExtension - Gradle likely needs these classes to be extendable
/**
 * Coroutines Gradle plugin.
 * <p>
 * Usage example...
 * <pre>
 * buildscript {
 *     repositories {
 *         mavenCentral() // change this to mavenLocal() if you're testing a local build of this plugin
 *     }
 * 
 *     dependencies {
 *         classpath group: 'com.offbynull.coroutines',  name: 'gradle-plugin',  version: 'PUT_CORRECT_VERSION_HERE'
 *     }
 * }
 * 
 * apply plugin: "java"
 * apply plugin: "coroutines"
 * 
 * coroutines {
 *     // Uncomment if you'll be stepping through your coroutines in an IDE.
 *     // debugMode = true 
 * }
 * 
 * repositories {
 *     mavenCentral()
 * }
 * 
 * dependencies {
 *     compile group: 'com.offbynull.coroutines', name: 'user', version: 'PUT_CORRECT_VERSION_HERE'
 * }
 * </pre>
 * @author Kasra Faghihi
 */
public class CoroutinesPlugin implements Plugin<Project> {

    private Logger log;

    @SuppressWarnings("unchecked")
    @Override
    public void apply(Project target) {
        // Add config block
        CoroutinesPluginConfiguration config = new CoroutinesPluginConfiguration();
        target.getExtensions().add("coroutines", config);

        Set<Task> compileJavaTasks = target.getTasksByName("compileJava", true);
        for (Task task : compileJavaTasks) {
            addInstrumentActionToTask("main", task, config);
        }

        Set<Task> compileJavaTestTasks = target.getTasksByName("compileTestJava", true);
        for (Task task : compileJavaTestTasks) {
            addInstrumentActionToTask("test", task, config);
        }

        log = target.getLogger();
    }

    @SuppressWarnings("unchecked")
    private void addInstrumentActionToTask(String sourceType, Task task, CoroutinesPluginConfiguration config) {
        task.doLast(x -> {
            try {
                // Get source sets -- since we don't have access to the normal Gradle plugins API (artifact can't be found on any repo) we
                // have to use Java reflections to access the data.
                Project proj = task.getProject();

                Object sourceSets = JXPathContext.newContext(proj).getValue("properties/sourceSets");

                // can't use JXPath for this because jxpath can't read inherited properties (getAsMap is inherited??)
                Map<String, Object> sourceSetsMap = (Map<String, Object>) MethodUtils.invokeMethod(sourceSets, "getAsMap");

                if (sourceSetsMap.containsKey(sourceType)) {
                    JXPathContext ctx = JXPathContext.newContext(sourceSetsMap);
                    File classesDir = (File) ctx.getValue(sourceType + "/output/classesDir");
                    Set<File> compileClasspath = (Set<File>) ctx.getValue(sourceType + "/compileClasspath/files");
                    
                    if (classesDir.isDirectory()) {
                        instrument(classesDir, compileClasspath, config);
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException("Coroutines instrumentation failed", e);
            }
        });        
    }
    
    private void instrument(File classesDir, Set<File> compileClasspath, CoroutinesPluginConfiguration config) {
        try {
            List<File> classpath = new ArrayList<>();
            classpath.add(classesDir); // change to destinationDir?
            log.debug("Getting compile classpath");
            classpath.addAll(compileClasspath);

            classpath = classpath.stream()
                    .filter(x -> x.exists())
                    .collect(toList());
            
            log.debug("Classpath for instrumentation is as follows: " + classpath);

            log.debug("Creating instrumenter...");
            MarkerType markerType = MarkerType.valueOf(config.getMarkerType());
            boolean debugMode = config.isDebugMode();
            boolean autoSerializable = config.isAutoSerializable();
            InstrumentationSettings settings = new InstrumentationSettings(markerType, debugMode, autoSerializable);
            Instrumenter instrumenter = new Instrumenter(classpath);

            // This logs to info by default, but info won't show up unless you pass -i to gradle. If you want logs to show up by default,
            // pass in log::lifecycle instead.
            PluginHelper.instrument(instrumenter, settings, classesDir, classesDir, log::info);
        } catch (IOException ioe) {
            throw new IllegalStateException("Failed to instrument", ioe);
        }
    }

}
//CHECKSTYLE.ON:DesignForExtension
