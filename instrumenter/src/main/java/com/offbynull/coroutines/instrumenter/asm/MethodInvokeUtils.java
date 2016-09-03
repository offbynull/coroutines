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

import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/**
 * Utility class to help with handling invocation instructions. 
 * @author Kasra Faghihi
 */
public final class MethodInvokeUtils {
    private MethodInvokeUtils() {
        // do nothing
    }
    
    /**
     * Get the number of arguments required for an invocation of some method. This includes the 'this' argument for non-static methods.
     * <p>
     * NOTE THAT THIS IS NOT THE NUMBER OF ITEMS ON THE STACK. If the method takes in doubles or longs, each double or long encountered
     * would be 2 items on the stack. This method returns the number of arguments required for the method to be invoked, not the number of
     * items required to be on the stack for the method to be invoked.
     * @param invokeNode the invocation instruction (either normal invocation or invokedynamic)
     * @return number of arguments required by this method
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code invokeNode} is neither of type {@link MethodInsnNode} nor {@link InvokeDynamicInsnNode},
     * or if type of invocation ({@link MethodInsnNode}) cannot be determined
     */
    public static int getArgumentCountRequiredForInvocation(AbstractInsnNode invokeNode) {
        Validate.notNull(invokeNode);

        if (invokeNode instanceof MethodInsnNode) {
            MethodInsnNode methodInsnNode = (MethodInsnNode) invokeNode;
            int extra;
            int paramCount;
            
            switch (methodInsnNode.getOpcode()) {
                case Opcodes.INVOKEVIRTUAL:
                case Opcodes.INVOKESPECIAL:
                case Opcodes.INVOKEINTERFACE:
                    extra = 1;
                    break;
                case Opcodes.INVOKESTATIC:
                    extra = 0;
                    break;
                default:
                    throw new IllegalArgumentException(); // unknown invocation type? probably badly generated instruction node
            }
            Type methodType = Type.getType(methodInsnNode.desc);
            paramCount = methodType.getArgumentTypes().length;
                    
            return paramCount + extra;
        } else if (invokeNode instanceof InvokeDynamicInsnNode) {
            InvokeDynamicInsnNode invokeDynamicInsnNode = (InvokeDynamicInsnNode) invokeNode;
            int paramCount;
            
            Type methodType = Type.getType(invokeDynamicInsnNode.desc);
            paramCount = methodType.getArgumentTypes().length;
            
            return paramCount;
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Get the return type of the method being invoked.
     * @param invokeNode the invocation instruction (either normal invocation or invokedynamic)
     * @return number of items required on the stack for this method
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code invokeNode} is neither of type {@link MethodInsnNode} nor {@link InvokeDynamicInsnNode},
     * or if type of invocation ({@link MethodInsnNode}) cannot be determined
     */
    public static Type getReturnTypeOfInvocation(AbstractInsnNode invokeNode) {
        Validate.notNull(invokeNode);

        if (invokeNode instanceof MethodInsnNode) {
            MethodInsnNode methodInsnNode = (MethodInsnNode) invokeNode;
            Type methodType = Type.getType(methodInsnNode.desc);
            return methodType.getReturnType();
        } else if (invokeNode instanceof InvokeDynamicInsnNode) {
            InvokeDynamicInsnNode invokeDynamicInsnNode = (InvokeDynamicInsnNode) invokeNode;
            Type methodType = Type.getType(invokeDynamicInsnNode.desc);
            return methodType.getReturnType();
        } else {
            throw new IllegalArgumentException();
        }
    }
    

}
