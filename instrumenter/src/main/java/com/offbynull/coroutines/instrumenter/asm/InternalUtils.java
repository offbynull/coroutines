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
package com.offbynull.coroutines.instrumenter.asm;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

final class InternalUtils {
    private InternalUtils() {
        // do nothing
    }

    static ClassInformation getClassInformation(final InputStream is) throws IOException {
        ClassReader classReader = new ClassReader(is);
        String name = classReader.getClassName();
        
        String superName = classReader.getSuperName();
        String[] interfaces = classReader.getInterfaces();
        boolean interfaceMarker = (classReader.getAccess() & Opcodes.ACC_INTERFACE) != 0;

        return new ClassInformation(name, superName, Arrays.asList(interfaces), interfaceMarker);
    }
}
