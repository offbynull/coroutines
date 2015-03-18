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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Utility class to search Java bytecode. 
 * @author Kasra Faghihi
 */
public final class SearchUtils {

    private SearchUtils() {
        // do nothing
    }

    /**
     * Find methods within a class with a specific name.
     * @param methodNodes method nodes to search through
     * @param name method name to search for
     * @return list of methods
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if any element in {@code expectedStartingParamTypes} is either of sort {@link Type#METHOD}
     * or {@link Type#VOID}
     */
    public static List<MethodNode> findMethodsWithName(Collection<MethodNode> methodNodes, String name) {
        Validate.notNull(methodNodes);
        Validate.notNull(name);
        Validate.noNullElements(methodNodes);
        
        
        List<MethodNode> ret = new ArrayList<>();
        for (MethodNode methodNode : methodNodes) {
            if (methodNode.name.equals(name)) {
                ret.add(methodNode);
            }
        }

        return ret;
    }

    /**
     * Find invocations of a certain method.
     * @param insnList instruction list to search through
     * @param expectedMethodType type of method being invoked
     * @return list of invocations (may be nodes of type {@link MethodInsnNode} or {@link InvokeDynamicInsnNode})
     * @throws NullPointerException if any argument is {@code null}
     * @throws NullPointerException if {@code expectedMethodType} isn't of sort {@link Type#METHOD}
     */
    public static List<AbstractInsnNode> findInvocationsOf(InsnList insnList, Type expectedMethodType) {
        Validate.notNull(insnList);
        Validate.notNull(expectedMethodType);
        Validate.isTrue(expectedMethodType.getSort() == Type.METHOD);

        List<AbstractInsnNode> ret = new ArrayList<>();
        
        Iterator<AbstractInsnNode> it = insnList.iterator();
        while (it.hasNext()) {
            AbstractInsnNode instructionNode = it.next();
            
            Type methodType;
            if (instructionNode instanceof MethodInsnNode) {
                MethodInsnNode methodInsnNode = (MethodInsnNode) instructionNode;
                methodType = Type.getType(methodInsnNode.desc);
            } else if (instructionNode instanceof InvokeDynamicInsnNode) {
                InvokeDynamicInsnNode invokeDynamicInsnNode = (InvokeDynamicInsnNode) instructionNode;
                methodType = Type.getType(invokeDynamicInsnNode.desc);
            } else {
                continue;
            }

            if (methodType.equals(expectedMethodType)) {
                ret.add(instructionNode);
            }
        }

        return ret;
    }

    /**
     * Find invocations of any method where the parameter list contains a type.
     * @param insnList instruction list to search through
     * @param expectedParamType parameter type
     * @return list of invocations (may be nodes of type {@link MethodInsnNode} or {@link InvokeDynamicInsnNode})
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code expectedParamType} is either of sort {@link Type#METHOD} or {@link Type#VOID}
     */
    public static List<AbstractInsnNode> findInvocationsWithParameter(InsnList insnList,
            Type expectedParamType) {
        Validate.notNull(insnList);
        Validate.notNull(expectedParamType);
        Validate.isTrue(expectedParamType.getSort() != Type.METHOD && expectedParamType.getSort() != Type.VOID);

        List<AbstractInsnNode> ret = new ArrayList<>();
        
        Iterator<AbstractInsnNode> it = insnList.iterator();
        while (it.hasNext()) {
            AbstractInsnNode instructionNode = it.next();
            Type[] methodParamTypes;
            if (instructionNode instanceof MethodInsnNode) {
                MethodInsnNode methodInsnNode = (MethodInsnNode) instructionNode;
                Type methodType = Type.getType(methodInsnNode.desc);
                methodParamTypes = methodType.getArgumentTypes();
            } else if (instructionNode instanceof InvokeDynamicInsnNode) {
                InvokeDynamicInsnNode invokeDynamicInsnNode = (InvokeDynamicInsnNode) instructionNode;
                Type methodType = Type.getType(invokeDynamicInsnNode.desc);
                methodParamTypes = methodType.getArgumentTypes();
            } else {
                continue;
            }

            if (Arrays.asList(methodParamTypes).contains(expectedParamType)) {
                ret.add(instructionNode);
            }
        }

        return ret;
    }

    /**
     * Find methods within a class where the parameter list contains a certain list of type.
     * @param methodNodes method nodes to search through
     * @param expectedParamType parameter type to search for
     * @return list of methods
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code expectedParamType} is either of sort {@link Type#METHOD} or {@link Type#VOID}
     */
    public static List<MethodNode> findMethodsWithParameter(Collection<MethodNode> methodNodes, Type expectedParamType) {
        Validate.notNull(methodNodes);
        Validate.notNull(expectedParamType);
        Validate.noNullElements(methodNodes);
        Validate.isTrue(expectedParamType.getSort() != Type.METHOD && expectedParamType.getSort() != Type.VOID);

        List<MethodNode> ret = new ArrayList<>();
        for (MethodNode methodNode : methodNodes) {
            Type methodDescType = Type.getType(methodNode.desc);
            Type[] methodParamTypes = methodDescType.getArgumentTypes();

            if (Arrays.asList(methodParamTypes).contains(expectedParamType)) {
                ret.add(methodNode);
            }
        }

        return ret;
    }
    
    /**
     * Find instructions in a certain class that are of a certain set of opcodes.
     * @param insnList instruction list to search through
     * @param opcodes opcodes to search for
     * @return list of instructions that contain the opcodes being searched for
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@coed opcodes} is empty
     */
    public static List<AbstractInsnNode> searchForOpcodes(InsnList insnList, int ... opcodes) {
        Validate.notNull(insnList);
        Validate.notNull(opcodes);
        Validate.isTrue(opcodes.length > 0);
        
        List<AbstractInsnNode> ret = new LinkedList<>();
        
        Set<Integer> opcodeSet = new HashSet<>();
        Arrays.stream(opcodes).forEach((x) -> opcodeSet.add(x));
        
        Iterator<AbstractInsnNode> it = insnList.iterator();
        while (it.hasNext()) {
            AbstractInsnNode insnNode = it.next();
            if (opcodeSet.contains(insnNode.getOpcode())) {
                ret.add(insnNode);
            }
        }
        
        return ret;
    }
}
