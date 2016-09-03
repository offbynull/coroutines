/*
 * Copyright (c) 2016, Kasra Faghihi, All rights reserved.
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

import org.apache.commons.lang3.reflect.MethodUtils;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;

public final class MethodInvokeUtilsTest {
    
    @Test
    public void mustProperlyDetermineStackSizeForNormalMethod() {
        Type type = Type.getType(MethodUtils.getAccessibleMethod(Integer.class, "compareTo", Integer.class));
        MethodInsnNode methodInsnNode = new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/util/Integer", "compareTo", type.getDescriptor(),
                false);
        int reqStackCount = MethodInvokeUtils.getArgumentCountRequiredForInvocation(methodInsnNode);

        assertEquals(2, reqStackCount);
    }

    @Test
    public void mustProperlyDetermineStackSizeForStaticMethod() {
        Type type = Type.getType(MethodUtils.getAccessibleMethod(Integer.class, "decode", String.class));
        MethodInsnNode methodInsnNode = new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Integer", "decode", type.getDescriptor(),
                false);
        int reqStackCount = MethodInvokeUtils.getArgumentCountRequiredForInvocation(methodInsnNode);

        assertEquals(1, reqStackCount);
    }
    
}
