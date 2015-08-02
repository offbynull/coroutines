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
import org.objectweb.asm.Type;

/**
 * Utility class to provide common functionality for types.
 * @author Kasra Faghihi
 */
public final class TypeUtils {
    private TypeUtils() {
        // do nothing
    }
    
    /**
     * Checks to see if one type is assignable from another type. This method should be similar to
     * {@link Class#isAssignableFrom(java.lang.Class) } (with some caveats, explained in next paragraph), but uses a
     * {@link ClassInformationRepository} object rather than requiring classes to be loaded up in to the JVM.
     * <p>
     * Note that this code tries to mimic what ASM's original {@link org.objectweb.asm.tree.analysis.SimpleVerifier} does to find out
     * if two types are equal. The main difference between SimpleVerifier's code and {@link Class#isAssignableFrom(java.lang.Class) } is
     * that SimpleVerifier will treat any interface instance as if it were an {@link Object} instance. That means that, for example, an
     * {@link Object} is assignable to a {@link Comparable} (Comparable = Object) in the eyes of ASM's SimpleVerifier. Why this is the case
     * has never been explained.
     * @param repo repository to use for deriving class details
     * @param t type being assigned from
     * @param u type being assigned to
     * @return {@code true} if u is assignable to t ({@code t = u}), {@code false} otherwise
     */
    public static boolean isAssignableFrom(ClassInformationRepository repo, Type t, Type u) {
        Validate.notNull(repo);
        Validate.notNull(t);
        Validate.notNull(u);
        
        if (t.equals(u)) {
            return true;
        }

        if (t.getSort() == Type.OBJECT && u.getSort() == Type.OBJECT) {
            // Both are objects, check hierarchy for both to see if assignable
            // e.g. you're allowed to do Number = Integer
            // e.g. you're allowed to do Serializable = Object, this seems counter-intuative but it is what ASM does
            return isObjectTypeAssignableFrom(repo, t, u);
        } else if (t.getSort() == Type.ARRAY && u.getSort() == Type.ARRAY) {
            // Both are arrays
            if (t.getDimensions() == u.getDimensions()) {
                // If dimensions are equal...
                Type tElem = t.getElementType();
                Type uElem = u.getElementType();
                if (tElem.getSort() == Type.OBJECT && uElem.getSort() == Type.OBJECT) {
                    // If dimensions are equal and both element types are objects, check hierarchy for both to see if assignable
                    // e.g. you're allowed to do Number[][] = Integer[][]
                    // e.g. you're allowed to do Object[][] = Integer[][]
                    // e.g. you're not allowed to do Serializable[][] = Object[][] because of false being passed in to method below...
                    //      we only want to resolve interfaces to object if we aren't dealing with arrays (see first if block at top of this
                    //      method)
                    return isArrayElementTypeAssignableFrom(repo, tElem, uElem);
                } else if (tElem.getSort() != Type.OBJECT && uElem.getSort() != Type.OBJECT) {
                    // If dimensions are equal and both element types are primitives, check that both are equal to see if assignable
                    // e.g. you're allowed to do int[][] = int[][]
                    // e.g. you're not allowed to do int[][] = byte[][]
                    // e.g. you're not allowed to do byte[][] = int[][]
                    return tElem.equals(uElem);
                } else {
                    // If dimensions are equal but you're dealing with one element type being an object and the other a primitive, always
                    // return false
                    // e.g. you're not allowed to do int[][] = Object[][]
                    // e.g. you're not allowed to do Object[][] = int[][]
                    return false;
                }
            } else if (t.getDimensions() > u.getDimensions()) {
                // If t has MORE dimensions than u, it is not assignable
                // e.g. you're not allowed to do Number[][] = Integer[]
                // e.g. you're not allowed to do Object[][] = Integer[]
                return false;
            } else if (t.getDimensions() < u.getDimensions()) {
                // If t has LESS dimensions than u, it is not assignable UNLESS t has an element type of Object
                // e.g. you're allowed to do Object[][] = Number[][][]
                // e.g. you're not allowed to do Number[][] = Integer[][][]
                // e.g. you're not allowed to do Object[][] = Integer[][][]
                return Type.getType(Object.class).equals(t.getElementType());
            }
        } else if (t.getSort() == Type.OBJECT && u.getSort() == Type.ARRAY) {
            // Assigning an array to an object, only valid if the object is of type Object
            // e.g. you're allowed to do Object = Integer[]
            return Type.getType(Object.class).equals(t);
        } else if (t.getSort() == Type.ARRAY && u.getSort() == Type.OBJECT) {
            // Assigning an array to an object, never valid
            // e.g. it doesn't make sense to do Integer[] = Object
            return false;
        }
        
        // What about primitives?

        return false;
    }
    
    private static boolean isObjectTypeAssignableFrom(ClassInformationRepository repo, Type t, Type u) {
        Validate.notNull(repo);
        Validate.notNull(t);
        Validate.notNull(u);
        Validate.isTrue(t.getSort() == Type.OBJECT);
        Validate.isTrue(u.getSort() == Type.OBJECT);
        
        ClassInformation ci = repo.getInformation(t.getInternalName());
        Validate.isTrue(ci != null, "Unable to find class information for %s", t);
        
        if (ci.isInterface()) { // special logic found in original SimpleVerifier moved here
            t = Type.getType(Object.class);
        }

        LinkedHashSet<String> hierarchy = flattenHierarchy(repo, u.getInternalName());
        return hierarchy.contains(t.getInternalName());
    }

    private static boolean isArrayElementTypeAssignableFrom(ClassInformationRepository repo, Type t, Type u) {
        Validate.notNull(repo);
        Validate.notNull(t);
        Validate.notNull(u);
        Validate.isTrue(t.getSort() == Type.OBJECT);
        Validate.isTrue(u.getSort() == Type.OBJECT);
        
        ClassInformation ci = repo.getInformation(t.getInternalName());
        Validate.isTrue(ci != null, "Unable to find class information for %s", t);

        LinkedHashSet<String> hierarchy = flattenHierarchy(repo, u.getInternalName());
        return hierarchy.contains(t.getInternalName());
    }

    private static LinkedHashSet<String> flattenHierarchy(ClassInformationRepository repo, String type) {
        Validate.notNull(repo);
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
            
            for (String interfaceType : classHierarchy.getInterfaces()) {
                ret.add(interfaceType);
            }
            
            currentType = classHierarchy.getSuperClassName();
        }
        
        return ret;
    }
}
