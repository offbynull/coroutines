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
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * An extension to ASM's {@link org.objectweb.asm.tree.analysis.SimpleVerifier} that uses {@link ClassInformationRepository} to dervie type
 * information rather than a classloader.
 * @author Kasra Faghihi
 */
public final class SimpleVerifier extends org.objectweb.asm.tree.analysis.SimpleVerifier {
    private final ClassInformationRepository repo;
    
    /**
     * Constructs a {@link SimpleVerifier} object.
     * @param repo class information repository to use for deriving class information
     * @throws NullPointerException if any argument is {@code null}
     */
    public SimpleVerifier(ClassInformationRepository repo) {
        super(Opcodes.ASM5, null, null, null, false);
        
        Validate.notNull(repo);
        
        this.repo = repo;
    }
    
    @Override
    protected boolean isInterface(final Type t) {
        return repo.getInformation(t.getInternalName()).isInterface();
    }

    @Override
    protected Type getSuperClass(final Type t) {
        String superClass = repo.getInformation(t.getInternalName()).getSuperClassName();
        return superClass == null ? null : Type.getObjectType(superClass);
    }

    @Override
    protected boolean isAssignableFrom(Type t, Type u) {
        if (t.equals(u)) {
            return true;
        }
        
        if (repo.getInformation(t.getInternalName()).isInterface()) {
            t = Type.getType(Object.class);
        }
        
        LinkedHashSet<String> hierarchy = flattenHierarchy(u.getInternalName());
        return hierarchy.contains(t.getInternalName());
    }

    private LinkedHashSet<String> flattenHierarchy(String type) {
        Validate.notNull(type);
        
        LinkedHashSet<String> ret = new LinkedHashSet<>();
        
        String currentType = type;
        while (true) {
            ret.add(currentType);
            
            ClassInformation classHierarchy = repo.getInformation(currentType); // must return a result
            Validate.isTrue(classHierarchy != null, "No parent found for %s", currentType);
            if (classHierarchy.getSuperClassName() == null) {
                break;
            }
            
            currentType = classHierarchy.getSuperClassName();
        }
        
        return ret;
    }
    
    @Override
    protected Class<?> getClass(final Type t) {
        throw new UnsupportedOperationException("Should never be called");
    }
}
