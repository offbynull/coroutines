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
package com.offbynull.coroutines.instrumenter.asm;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.compress.archivers.jar.JarArchiveEntry;
import org.apache.commons.compress.archivers.jar.JarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

/**
 * Provides information on classes contained within JARs and folders.
 * @author Kasra Faghihi
 */
public final class FileSystemClassInformationRepository implements ClassInformationRepository {
    private final Map<String, ClassInformation> hierarchyMap = new HashMap<>();

    /**
     * Constructs a {@link FileSystemClassInformationRepository} object and loads it up with the classes in a classpath.
     * @param initialClasspath classpath to scan for class information (can be JAR files and/or folders)
     * @return newly created {@link FileSystemClassInformationRepository} object
     * @throws NullPointerException if any argument is {@code null} or contains {@code null} elements
     * @throws IOException if an IO error occurs
     */
    public static FileSystemClassInformationRepository create(List<File> initialClasspath) throws IOException {
        Validate.notNull(initialClasspath);
        Validate.noNullElements(initialClasspath);
        FileSystemClassInformationRepository repo = new FileSystemClassInformationRepository();
        repo.addClasspath(initialClasspath);
        return repo;
    }
    
    @Override
    public ClassInformation getInformation(String internalClassName) {
        return hierarchyMap.get(internalClassName);
    }

    /**
     * Add a custom class.
     * @param className name of class
     * @param classInformation information for class
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code className} already exists in this repository
     */
    public void addIndividual(String className, ClassInformation classInformation) {
        Validate.notNull(className);
        Validate.notNull(classInformation);
        Validate.isTrue(!hierarchyMap.containsKey(className));
        
        hierarchyMap.put(className, classInformation);
    }

    /**
     * Add classes contained within a list of JAR files and folders. Note that if a duplicate class is encountered, the original is kept.
     * @param classpath list of JARs and folders to scan
     * @throws NullPointerException if any argument is {@code null} or contains {@code null} elements
     * @throws IOException if an IO error occurs
     */
    public void addClasspath(List<File> classpath) throws IOException {
        Validate.notNull(classpath);
        Validate.noNullElements(classpath);

        for (File classpathElement : classpath) {
            if (classpathElement.isFile()) {
                addJar(classpathElement);
            } else if (classpathElement.isDirectory()) {
                addDirectory(classpathElement);
            } else {
                throw new IllegalStateException();
            }
        }
    }
    
    private void addDirectory(File directory) throws IOException {
        Validate.notNull(directory);
        Validate.isTrue(directory.isDirectory());
        for (File file : FileUtils.listFiles(directory, new String[] {"class"}, true)) {
            if (!file.getName().endsWith(".class")) {
                continue;
            }
            
            try (InputStream is = new FileInputStream(file)) {
                populateSuperClassMapping(is);
            }
        }
    }

    private void addJar(File file) throws IOException {
        Validate.notNull(file);
        Validate.isTrue(file.isFile());
        try (FileInputStream fis = new FileInputStream(file);
                JarArchiveInputStream jais = new JarArchiveInputStream(fis)) {
            JarArchiveEntry entry;
            while ((entry = jais.getNextJarEntry()) != null) {
                if (!entry.getName().endsWith(".class") || entry.isDirectory()) {
                    continue;
                }

                populateSuperClassMapping(jais);
            }
        }
    }
    
    private void populateSuperClassMapping(final InputStream is) throws IOException {
        ClassReader classReader = new ClassReader(is);
        String name = classReader.getClassName();
        
        if (hierarchyMap.containsKey(name)) {
            // duplicate encounter, ignore
            return;
        }
        
        String superName = classReader.getSuperName();
        String[] interfaces = classReader.getInterfaces();
        boolean interfaceMarker = (classReader.getAccess() & Opcodes.ACC_INTERFACE) != 0;

        hierarchyMap.put(name, new ClassInformation(superName, Arrays.asList(interfaces), interfaceMarker));
    }
    
}
