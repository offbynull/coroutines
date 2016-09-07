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
package com.offbynull.coroutines.instrumenter.generators;

import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/**
 * Utility class to generate bytecode instructions that help debug instrumented code.
 * @author Kasra Faghihi
 */
public final class DebugGenerators {
    
    private DebugGenerators() {
        // do nothing
    }

    /**
     * Generates instructions for generating marker instructions. These marker instructions are meant to be is useful for debugging
     * instrumented code. For example, you can spot a specific portion of instrumented code by looking for specific markers in the assembly
     * output.
     * @param markerType marker type (determines what kind of instructions are generated)
     * @param text text to print out
     * @return instructions to call System.out.println with a string constant
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList debugMarker(MarkerType markerType, String text) {
        Validate.notNull(markerType);
        Validate.notNull(text);
        
        InsnList ret = new InsnList();
        
        switch (markerType) {
            case NONE:
                break;
            case CONSTANT:
                ret.add(new LdcInsnNode(text));
                ret.add(new InsnNode(Opcodes.POP));
                break;
            case STDOUT:
                ret.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
                ret.add(new LdcInsnNode(text));
                ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false));
                break;
            default:
                throw new IllegalStateException();
        }

        return ret;
    }
    
    /**
     * Debug marker type.
     */
    public enum MarkerType {
        /**
         * Generate no instructions.
         */
        NONE,
        /**
         * Generate instructions to load text as string constant and immediately pop it off the stack.
         */
        CONSTANT,
        /**
         * Generate instructions to print text to standard out.
         */
        STDOUT
    }

    /**
     * Generates instructions for printing out a string using {@link System#out}. This is useful for debugging. For example, you
     * can print out lines around your instrumented code to make sure that what you think is being run is actually being run.
     * @param text debug text generation instruction list -- must leave a String on the stack
     * @return instructions to call System.out.println with a string constant
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList debugPrint(InsnList text) {
        Validate.notNull(text);
        
        InsnList ret = new InsnList();
        
        ret.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
        ret.add(text);
        ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false));

        return ret;
    }
}
