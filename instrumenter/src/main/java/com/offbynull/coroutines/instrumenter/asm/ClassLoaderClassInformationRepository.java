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

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Type;

/**
 * Provides information on classes contained within a {@link ClassLoader}.
 * @author Kasra Faghihi
 */
public final class ClassLoaderClassInformationRepository implements ClassInformationRepository {
    private final ClassLoader classLoader;

    /**
     * Constructs a {@link ClassLoaderClassInformationRepository} object.
     * @param classLoader classloader to extract information from
     * @throws NullPointerException if any argument is {@code null}
     */
    public ClassLoaderClassInformationRepository(ClassLoader classLoader) {
        Validate.notNull(classLoader);
        this.classLoader = classLoader;
    }

    @Override
    public ClassInformation getInformation(String internalClassName) {
        String className = Type.getObjectType(internalClassName).getClassName();
        Class<?> cls;
        try {
            cls = Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException cfne) {
            return null;
        }
        
        
        boolean interfaceMarker = cls.isInterface();
        
        Class<?>[] interfaceClses = cls.getInterfaces();
        List<String> internalInterfaceNames = new ArrayList<>(interfaceClses.length);
        for (Class<?> interfaceCls : interfaceClses) {
            internalInterfaceNames.add(Type.getInternalName(interfaceCls));
        }
        
        String internalSuperClassName;
        if (interfaceMarker) {
            // If this is an interface, it needs to mimic how class files are structured. Normally a class file will have its super class
            // set to java/lang/Object if it's an interface. This isn't exposed through the classloader. Not sure why this is like this but
            // if we encounter an interface as the class being accessed through the classloader, always override it to have a "superclass"
            // of java/lang/Object. The classloader itself would return null in this case.
            //
            // This is what ASM does internally as well when it loads info from a classloader.
            internalSuperClassName = Type.getInternalName(Object.class);
        } else {
            Class<?> superCls = cls.getSuperclass();
            internalSuperClassName = superCls == null ? null : Type.getInternalName(superCls);
        }

        return new ClassInformation(internalSuperClassName, internalInterfaceNames, interfaceMarker);
    }
}
