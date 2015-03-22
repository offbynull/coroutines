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

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.tree.ClassNode;

/**
 * A {@link ClassNode} that overrides {@link #visitMethod(int, java.lang.String, java.lang.String, java.lang.String, java.lang.String[])  }
 * such that it uses {@link JSRInlinerAdapter} to inline JSR blocks in the class. Try-catch-finally in earlier versions of Java use the JSR
 * opcode, which may end up causing problems during analysis.
 * @author Kasra Faghihi
 */
public final class SimpleClassNode extends ClassNode {

    /**
     * Constructs a {@link SimpleClassNode} object.
     */
    public SimpleClassNode() {
        super(Opcodes.ASM5);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor origVisitor = super.visitMethod(access, name, desc, signature, exceptions);
        return new JSRInlinerAdapter(origVisitor, access, name, desc, signature, exceptions);
    }
}
