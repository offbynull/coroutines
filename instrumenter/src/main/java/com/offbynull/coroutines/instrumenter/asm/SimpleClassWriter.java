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

import java.util.LinkedHashSet;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/**
 * A {@link ClassWriter} that overrides {@link #getCommonSuperClass(java.lang.String, java.lang.String) } such that it uses
 * {@link ClassInformationRepository} to derive the common super rather than querying loaded up classes.
 * @author Kasra Faghihi
 */
public final class SimpleClassWriter extends ClassWriter {

    private final ClassInformationRepository infoRepo;
    
    /**
     * Constructs a {@link SimpleClassWriter} object. See {@link ClassWriter#ClassWriter(int) }.
     * @param flags option flags that can be used to modify the default behavior of this class. See {@link ClassWriter#COMPUTE_MAXS},
     * {@link ClassWriter#COMPUTE_FRAMES}.
     * @param infoRepo class hierarchy mappings for deriving stack map frames
     * @throws NullPointerException if any argument is {@code null}
     */
    public SimpleClassWriter(int flags, ClassInformationRepository infoRepo) {
        super(flags);
        Validate.notNull(infoRepo);
        this.infoRepo = infoRepo;
    }

    /**
     * Constructs a {@link SimpleClassWriter} object. See {@link ClassWriter#ClassWriter(org.objectweb.asm.ClassReader, int) }.
     * @param classReader the {@link ClassReader} used to read the original class. It will be used to copy the entire constant pool from the
     * original class and also to copy other fragments of original bytecode where applicable.
     * @param flags option flags that can be used to modify the default behavior of this class. See {@link ClassWriter#COMPUTE_MAXS},
     * {@link ClassWriter#COMPUTE_FRAMES}.
     * @param infoRepo class hierarchy mappings for deriving stack map frames
     * @throws NullPointerException if any argument is {@code null}
     */
    public SimpleClassWriter(ClassReader classReader, int flags, ClassInformationRepository infoRepo) {
        super(classReader, flags);
        Validate.notNull(classReader);
        Validate.notNull(infoRepo);
        this.infoRepo = infoRepo;
    }
    
    /**
     * Derives common super class from the super name mapping passed in to the constructor.
     * @param type1 the internal name of a class.
     * @param type2 the internal name of another class.
     * @return the internal name of the common super class of the two given classes
     * @throws NullPointerException if any argument is {@code null}
     */
    @Override
    protected String getCommonSuperClass(final String type1, final String type2) {
        Validate.notNull(type1);
        Validate.notNull(type2);
        
        infoRepo.getInformation(type1);
        LinkedHashSet<String> type1Hierarchy = flattenHierarchy(type1);
        LinkedHashSet<String> type2Hierarchy = flattenHierarchy(type2);
        
        for (String testType1 : type1Hierarchy) {
            for (String testType2 : type2Hierarchy) {
                if (testType1.equals(testType2)) {
                    return testType1;
                }
            }
        }
        
        return "java/lang/Object"; // is this correct behaviour? shouldn't both type1 and type2 ultimately contain Object?
    }
    
    private LinkedHashSet<String> flattenHierarchy(String type) {
        Validate.notNull(type);
        
        LinkedHashSet<String> ret = new LinkedHashSet<>();
        
        String currentType = type;
        while (true) {
            ret.add(currentType);
            
            ClassInformation classHierarchy = infoRepo.getInformation(currentType); // must return a result
            Validate.isTrue(classHierarchy != null, "No parent found for %s", currentType);
            if (classHierarchy.getSuperClassName() == null) {
                break;
            }
            
            currentType = classHierarchy.getSuperClassName();
        }
        
        return ret;
    }
}
