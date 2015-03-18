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
package com.offbynull.coroutines.instrumenter.testhelpers;

import com.offbynull.coroutines.instrumenter.Instrumenter;
import com.offbynull.coroutines.instrumenter.asm.SimpleClassWriter;
import com.offbynull.coroutines.instrumenter.asm.ClassInformationRepository;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.compress.archivers.jar.JarArchiveEntry;
import org.apache.commons.compress.archivers.jar.JarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/**
 * Test utilities.
 *
 * @author Kasra Faghihi
 */
public final class TestUtils {

    private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";
    private static final String MANIFEST_TEXT = "Manifest-Version: 1.0\r\nCreated-By: " + TestUtils.class.getName();

    private TestUtils() {
        // do nothing
    }
    
    /**
     * Opens up a ZIP resource, instruments the classes within, and returns a {@link URLClassLoader} object with access to those classes.
     * @param path path of zip resource
     * @return class loader able to access instrumented classes
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException if an IO error occurs
     */
    public static URLClassLoader loadClassesInZipResourceAndInstrument(String path) throws IOException {
        Validate.notNull(path);
        
        // Load original class
        Map<String, byte[]> classContents = readZipFromResource(path);
        
        // Create JAR out of original classes so it can be found by the instrumenter
        List<JarEntry> originalJarEntries = new ArrayList<>(classContents.size());
        for (Entry<String, byte[]> entry : classContents.entrySet()) {
            originalJarEntries.add(new JarEntry(entry.getKey(), entry.getValue()));
        }
        File originalJarFile = createJar(originalJarEntries.toArray(new JarEntry[0]));
        
        // Get classpath used to run this Java process and addIndividual the jar file we created to it (used by the instrumenter)
        List<File> classpath = getClasspath();
        classpath.add(originalJarFile);
        
        // Instrument classes and write out new jar
        Instrumenter instrumenter = new Instrumenter(classpath);
        List<JarEntry> instrumentedJarEntries = new ArrayList<>(classContents.size());
        for (Entry<String, byte[]> entry : classContents.entrySet()) {
            byte[] content = entry.getValue();
            if (entry.getKey().endsWith(".class")) {
                content = instrumenter.instrument(content);
            }
            instrumentedJarEntries.add(new JarEntry(entry.getKey(), content));
        }
        File instrumentedJarFile = createJar(instrumentedJarEntries.toArray(new JarEntry[0]));
        
        // Load up classloader with instrumented jar
        return URLClassLoader.newInstance(new URL[] { instrumentedJarFile.toURI().toURL() }, TestUtils.class.getClassLoader());
    }
    
    /**
     * Load up a ZIP resource from the classpath and generate a {@link ClassNode} for each file with a class extension in that ZIP.
     * Behaviour is ZIP or classes within are not a parseable.
     * @param path path of ZIP resource
     * @return {@link ClassNode} representation of class files in ZIP
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException if any IO error occurs
     * @throws IllegalArgumentException if {@code path} cannot be found
     */
    public static Map<String, ClassNode> readZipResourcesAsClassNodes(String path) throws IOException {
        Validate.notNull(path);
        
        Map<String, byte[]> files = readZipFromResource(path);
        Map<String, ClassNode> ret = new LinkedHashMap<>();
        for (Entry<String, byte[]> entry : files.entrySet()) {
            if (!entry.getKey().toLowerCase().endsWith(".class")) {
                continue;
            }
            
            ClassReader cr = new ClassReader(new ByteArrayInputStream(entry.getValue()));
            ClassNode classNode = new ClassNode();
            cr.accept(classNode, 0);
            
            ret.put(entry.getKey(), classNode);
        }

        return ret;
    }

    /**
     * Writes {@link ClassNode}s to a JAR and loads it up. Behaviour is unknown if class is not valid in some way.
     * @param classNodes class nodes to put in to jar
     * @return class loader with files in newly created JAR available
     * @throws IOException if any IO error occurs
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if {@code classNodes} is empty
     */
    public static URLClassLoader createJarAndLoad(ClassNode ... classNodes) throws IOException {
        Validate.notNull(classNodes);
        Validate.noNullElements(classNodes);
        
        ClassInformationRepository infoRepo = ClassInformationRepository.create(getClasspath());
        SimpleClassWriter cw = new SimpleClassWriter(SimpleClassWriter.COMPUTE_MAXS | SimpleClassWriter.COMPUTE_FRAMES, infoRepo);
        
        JarEntry[] jarEntries = new JarEntry[classNodes.length];
        for (int i = 0; i < jarEntries.length; i++) {
            classNodes[i].accept(cw);

            jarEntries[i] = new JarEntry(classNodes[i].name + ".class", cw.toByteArray());
        }
        
        return createJarAndLoad(jarEntries);
    }

    /**
     * Writes entries to a JAR and loads it up.
     * @param entries class nodes to put in to jar
     * @return class loader with files in newly created JAR available
     * @throws IOException if any IO error occurs
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if {@code classNodes} is empty
     */
    public static URLClassLoader createJarAndLoad(JarEntry ... entries) throws IOException {
        Validate.notNull(entries);
        Validate.noNullElements(entries);
        
        File jarFile = createJar(entries);
        return URLClassLoader.newInstance(new URL[] { jarFile.toURI().toURL() }, TestUtils.class.getClassLoader());
    }
    
    /**
     * Loads up a ZIP file that's contained in the classpath.
     * @param path path of ZIP resource
     * @return contents of files within the ZIP
     * @throws IOException if any IO error occurs
     * @throws NullPointerException if any argument is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code path} cannot be found, or if zipPaths contains duplicates
     */
    public static Map<String, byte[]> readZipFromResource(String path) throws IOException {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        URL url = cl.getResource(path);
        Validate.isTrue(url != null);
        
        Map<String, byte[]> ret = new LinkedHashMap<>();
        
        try (InputStream is = url.openStream();
                ZipArchiveInputStream zais = new ZipArchiveInputStream(is)) {
            ZipArchiveEntry entry;
            while ((entry = zais.getNextZipEntry()) != null) {
                ret.put(entry.getName(), IOUtils.toByteArray(zais));
            }
        }
        
        return ret;
    }
    
    /**
     * Loads up a resource from the classpath as a byte array.
     * @param path path of resource
     * @return contents of resource
     * @throws IOException if any IO error occurs
     * @throws IllegalArgumentException if {@code path} cannot be found
     */
    public static byte[] getResource(String path) throws IOException {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        URL url = cl.getResource(path);
        Validate.isTrue(url != null);
        try (InputStream in = url.openStream()) {
            return IOUtils.toByteArray(in);
        }
    }

    /**
     * Gets the system classpath and creates a list of {@link File} objects for each path.
     *
     * @return classpath as list of {@link File} objects
     * @throws IllegalStateException if the classpath doesn't exist or can't be split up
     */
    public static List<File> getClasspath() {
        String pathSeparator = System.getProperty("path.separator");
        Validate.validState(pathSeparator != null);
        
        String classpath = System.getProperty("java.class.path");
        Validate.validState(classpath != null);
        List<File> classPathFiles = Arrays
                .stream(classpath.split(Pattern.quote(pathSeparator)))
                .map(x -> new File(x))
                .filter(x -> x.exists())
                .collect(Collectors.toList());

        String bootClasspath = System.getProperty("sun.boot.class.path");
        Validate.validState(bootClasspath != null);
        List<File> bootClassPathFiles = Arrays
                .stream(bootClasspath.split(Pattern.quote(pathSeparator)))
                .map(x -> new File(x))
                .filter(x -> x.exists())
                .collect(Collectors.toList());

        ArrayList<File> ret = new ArrayList<>();
        ret.addAll(classPathFiles);
        ret.addAll(bootClassPathFiles);
        
        return ret;
    }

    /**
     * Creates a JAR as a temporary file. Simple manifest file will automatically be inserted.
     *
     * @param entries files to put inside of the jar
     * @return resulting jar file
     * @throws IOException if any IO errors occur
     * @throws NullPointerException if any argument is {@code null} or contains {@code null} elements
     */
    public static File createJar(JarEntry... entries) throws IOException {
        Validate.notNull(entries);
        Validate.noNullElements(entries);

        File tempFile = File.createTempFile(TestUtils.class.getSimpleName(), ".jar");
        tempFile.deleteOnExit();

        try (FileOutputStream fos = new FileOutputStream(tempFile);
                JarArchiveOutputStream jaos = new JarArchiveOutputStream(fos)) {
            writeJarEntry(jaos, MANIFEST_PATH, MANIFEST_TEXT.getBytes(Charsets.UTF_8));
            for (JarEntry entry : entries) {
                writeJarEntry(jaos, entry.name, entry.data);
            }
        }

        return tempFile;
    }

    private static void writeJarEntry(JarArchiveOutputStream jaos, String name, byte[] data) throws IOException {
        ZipArchiveEntry entry = new JarArchiveEntry(name);
        entry.setSize(data.length);
        jaos.putArchiveEntry(entry);
        jaos.write(data);
        jaos.closeArchiveEntry();
    }

    /**
     * An entry for a JAR file.
     */
    public static final class JarEntry {

        private final String name;
        private final byte[] data;

        /**
         * Constructs a {@link JarEntry} object.
         *
         * @param name path of the file within the jar
         * @param data contents of the jar
         * @throws NullPointerException if any argument is {@code null}
         * @throws IllegalArgumentException if {@code name} is empty
         */
        public JarEntry(String name, byte[] data) {
            Validate.notNull(name);
            Validate.notNull(data);
            Validate.isTrue(!name.isEmpty());
            this.name = name;
            this.data = data.clone();
        }
    }
}
