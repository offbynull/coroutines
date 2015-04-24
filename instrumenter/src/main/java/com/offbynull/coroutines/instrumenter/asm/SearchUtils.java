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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

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
     * @param expectedMethod type of method being invoked
     * @return list of invocations (may be nodes of type {@link MethodInsnNode} or {@link InvokeDynamicInsnNode})
     * @throws NullPointerException if any argument is {@code null}
     * @throws NullPointerException if {@code expectedMethodType} isn't of sort {@link Type#METHOD}
     */
    public static List<AbstractInsnNode> findInvocationsOf(InsnList insnList, Method expectedMethod) {
        Validate.notNull(insnList);
        Validate.notNull(expectedMethod);

        List<AbstractInsnNode> ret = new ArrayList<>();
        
        Type expectedMethodDesc = Type.getType(expectedMethod);
        Type expectedMethodOwner = Type.getType(expectedMethod.getDeclaringClass());
        String expectedMethodName = expectedMethod.getName();
        
        Iterator<AbstractInsnNode> it = insnList.iterator();
        while (it.hasNext()) {
            AbstractInsnNode instructionNode = it.next();
            
            Type methodDesc;
            Type methodOwner;
            String methodName;
            if (instructionNode instanceof MethodInsnNode) {
                MethodInsnNode methodInsnNode = (MethodInsnNode) instructionNode;
                methodDesc = Type.getType(methodInsnNode.desc);
                methodOwner = Type.getObjectType(methodInsnNode.owner);
                methodName = expectedMethod.getName();
            } else {
                continue;
            }

            if (methodDesc.equals(expectedMethodDesc) && methodOwner.equals(expectedMethodOwner) && methodName.equals(expectedMethodName)) {
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
     * @throws IllegalArgumentException if {@code opcodes} is empty
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
    
    /**
     * Get the number of items that need to be on the stack for an invocation of some method.
     * @param invokeNode the invocation instruction (either normal invocation or invokedynamic)
     * @return number of items required on the stack for this method
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code invokeNode} is neither of type {@link MethodInsnNode} nor {@link InvokeDynamicInsnNode},
     * or if type of invocation ({@link MethodInsnNode}) cannot be determined
     */
    public static int getRequiredStackCountForInvocation(AbstractInsnNode invokeNode) {
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
    
    /**
     * Find trycatch blocks within a method that an instruction is apart of. Only includes the try portion, not the catch (handler) portion.
     * @param insnList instruction list for method
     * @param tryCatchBlockNodes trycatch blocks in method
     * @param insnNode instruction within method being searched against
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if arguments aren't all from the same method
     * @return items from {@code tryCatchBlockNodes} that {@code insnNode} is a part of
     */
    public static List<TryCatchBlockNode> findTryCatchBlockNodesEncompassingInstruction(InsnList insnList,
            List<TryCatchBlockNode> tryCatchBlockNodes, AbstractInsnNode insnNode) {
        Validate.notNull(insnList);
        Validate.notNull(tryCatchBlockNodes);
        Validate.notNull(insnNode);
        Validate.noNullElements(tryCatchBlockNodes);
        
        Map<LabelNode, Integer> labelPositions = new HashMap<>();
        int insnNodeIdx = -1;
        
        // Get index of labels and insnNode within method
        ListIterator<AbstractInsnNode> insnIt = insnList.iterator();
        int insnCounter = 0;
        while (insnIt.hasNext()) {
            AbstractInsnNode node = insnIt.next();
            
            // If our instruction, save index
            if (node == insnNode) {
                if (insnNodeIdx == -1) {
                    insnNodeIdx = insnCounter;
                } else {
                    throw new IllegalArgumentException(); // insnNode encountered multiple times in methodNode. Should not happen.
                }
            }
            
            // If label node, save position
            if (node instanceof LabelNode) {
                labelPositions.put((LabelNode) node, insnCounter);
            }
            
            // Increment counter
            insnCounter++;
        }
        
        Validate.isTrue(insnNodeIdx != -1); //throw exception if node not in method list
        
        
        
        // Find out which trycatch blocks insnNode is within
        List<TryCatchBlockNode> ret = new ArrayList<>();
        for (TryCatchBlockNode tryCatchBlockNode : tryCatchBlockNodes) {
            Integer startIdx = labelPositions.get(tryCatchBlockNode.start);
            Integer endIdx = labelPositions.get(tryCatchBlockNode.end);
            
            Validate.isTrue(startIdx != null);
            Validate.isTrue(endIdx != null);
            
            if (insnNodeIdx >= startIdx && insnNodeIdx < endIdx) {
                ret.add(tryCatchBlockNode);
            }
        }
        
        return ret;
    }

    /**
     * Find line number associated with an instruction.
     * @param insnList instruction list for method
     * @param insnNode instruction within method being searched against
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if arguments aren't all from the same method
     * @return line number node associated with the instruction, or {@code null} if no line number exists
     */
    public static LineNumberNode findLineNumberForInstruction(InsnList insnList, AbstractInsnNode insnNode) {
        Validate.notNull(insnList);
        Validate.notNull(insnNode);
        
        int idx = insnList.indexOf(insnNode);
        Validate.isTrue(idx != -1);
        
        // Get index of labels and insnNode within method
        ListIterator<AbstractInsnNode> insnIt = insnList.iterator(idx);
        while (insnIt.hasPrevious()) {
            AbstractInsnNode node = insnIt.previous();
            
            if (node instanceof LineNumberNode) {
                return (LineNumberNode) node;
            }
        }
        
        return null;
    }
}
