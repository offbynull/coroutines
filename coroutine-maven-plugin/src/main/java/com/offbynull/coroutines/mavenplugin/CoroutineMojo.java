/*
 * Copyright (c) 2015, Kasra Faghihi, All rights reserved.
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
package com.offbynull.coroutines.mavenplugin;

import com.offbynull.coroutines.instrumenter.Instrumenter;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Mojo to run coroutine instrumentation. Instruments both main classes and test classes.
 * <p>
 * Sample usage in POM:
 * <pre>
 *     &lt;build&gt;
 *         &lt;plugins&gt;
 *             &lt;plugin&gt;
 *                 &lt;groupId&gt;com.offbynull.coroutines&lt;/groupId&gt;
 *                 &lt;artifactId&gt;coroutine-maven-plugin&lt;/artifactId&gt;
 *                 &lt;version&gt;1.0.0-SNAPSHOT&lt;/version&gt;
 *                 &lt;executions&gt;
 *                     &lt;execution&gt;
 *                         &lt;goals&gt;
 *                             &lt;goal&gt;instrument&lt;/goal&gt;
 *                         &lt;/goals&gt;
 *                     &lt;/execution&gt;
 *                 &lt;/executions&gt;
 *             &lt;/plugin&gt;
 *         &lt;/plugins&gt;
 *     &lt;/build&gt;
 * </pre>
 * 
 * or directly call the goal instrument (e.g. mvn coroutine:instrument)
 *
 * @author Kasra Faghihi
 */
@Mojo(name = "instrument", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE)
public final class CoroutineMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;
    
    @Parameter(defaultValue = "${java.home}/lib", required = true)
    private String jdkLibsDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Log log = getLog();
        
        List<File> classpath;
        try {
            log.debug("Getting compile classpath");
            classpath = ((List<String>) project.getCompileClasspathElements())
                    .stream().map(x -> new File(x)).collect(Collectors.toList());
            log.debug("Getting bootstrap classpath");
            classpath.addAll(FileUtils.listFiles(new File(jdkLibsDirectory), new String[] {"jar"}, true));
            
            log.info("Classpath for instrumentation is as follows: " + classpath);
        } catch (Exception ex) {
            throw new MojoExecutionException("Unable to get compile classpath elements", ex);
        }

        Instrumenter instrumenter;
        try {
            log.info("Creating instrumenter...");
            instrumenter = new Instrumenter(classpath);

            File mainOutputFolder = new File(project.getBuild().getOutputDirectory());
            log.info("Scanning main output folder ... ");
            instrumentPath(log, instrumenter, mainOutputFolder);
            
            File testOutputFolder = new File(project.getBuild().getTestOutputDirectory());
            if (testOutputFolder.isDirectory()) {
                log.info("Processing test output folder...");
                instrumentPath(log, instrumenter, new File(project.getBuild().getTestOutputDirectory()));
            }
        } catch (Exception ex) {
            throw new MojoExecutionException("Failed to instrument", ex);
        }
    }

    private void instrumentPath(Log log, Instrumenter instrumenter, File path) throws IOException {
        for (File classFile : FileUtils.listFiles(path, new String[] {"class"}, true)) {
            log.info("Instrumenting " + classFile);
            byte[] input = FileUtils.readFileToByteArray(classFile);
            byte[] output = instrumenter.instrument(input);
            log.debug("File size changed from " + input.length + " to " + output.length);
            FileUtils.writeByteArrayToFile(classFile, output);
        }
    }
}
