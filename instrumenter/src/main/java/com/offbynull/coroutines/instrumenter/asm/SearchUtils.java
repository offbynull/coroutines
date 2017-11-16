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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
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
     * Find static methods within a class.
     * @param methodNodes method nodes to search through
     * @return list of methods
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     */
    public static List<MethodNode> findStaticMethods(Collection<MethodNode> methodNodes) {
        Validate.notNull(methodNodes);
        Validate.noNullElements(methodNodes);
        
        
        List<MethodNode> ret = new ArrayList<>();
        for (MethodNode methodNode : methodNodes) {
            if ((methodNode.access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC) {
                ret.add(methodNode);
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
     * Find methods within a class where the parameter list contains a certain list of type.
     * @param methodNodes method nodes to search through
     * @param paramTypes parameter types to search for (in the order specified)
     * @return list of methods
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if any element in {@code paramTypes} is either of sort {@link Type#METHOD} or {@link Type#VOID}
     */
    public static List<MethodNode> findMethodsWithParameters(Collection<MethodNode> methodNodes, Type ... paramTypes) {
        Validate.notNull(methodNodes);
        Validate.notNull(paramTypes);
        Validate.noNullElements(methodNodes);
        Validate.noNullElements(paramTypes);
        for (Type paramType : paramTypes) {
            Validate.isTrue(paramType.getSort() != Type.METHOD && paramType.getSort() != Type.VOID);
        }

        List<MethodNode> ret = new ArrayList<>();
        for (MethodNode methodNode : methodNodes) {
            Type methodDescType = Type.getType(methodNode.desc);
            Type[] methodParamTypes = methodDescType.getArgumentTypes();

            if (methodParamTypes.length != paramTypes.length) {
                continue;
            }

            boolean found = true;
            for (int i = 0; i < methodParamTypes.length; i++) {
                if (!paramTypes[i].equals(methodParamTypes[i])) {
                    found = false;
                }
            }

            if (found) {
                ret.add(methodNode);
            }
        }

        return ret;
    }

    /**
     * Find a method within a class.
     * @param methodNodes method nodes to search through
     * @param name method name to search for
     * @param isStatic {@code true} if the method should be static
     * @param returnType return type to search for
     * @param paramTypes parameter types to search for (in the order specified)
     * @return method found (or {@code null} if no method could be found)
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if any element of  {@code paramTypes} is either of sort {@link Type#METHOD} or {@link Type#VOID}, or
     * if {@code returnType} is {@link Type#METHOD}
     */
    public static MethodNode findMethod(Collection<MethodNode> methodNodes, boolean isStatic, Type returnType, String name,
            Type ... paramTypes) {
        Validate.notNull(methodNodes);
        Validate.notNull(returnType);
        Validate.notNull(name);
        Validate.notNull(paramTypes);
        Validate.noNullElements(methodNodes);
        Validate.noNullElements(paramTypes);
        Validate.isTrue(returnType.getSort() != Type.METHOD);
        for (Type paramType : paramTypes) {
            Validate.isTrue(paramType.getSort() != Type.METHOD && paramType.getSort() != Type.VOID);
        }
        
        Collection<MethodNode> ret = methodNodes;
        
        ret = findMethodsWithName(ret, name);
        ret = findMethodsWithParameters(ret, paramTypes);
        if (isStatic) {
            ret = findStaticMethods(ret);
        }
        
        Validate.validState(ret.size() <= 1); // sanity check -- should never get triggered

        return ret.isEmpty() ? null : ret.iterator().next();
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

    /**
     * Find local variable node for a local variable at some instruction.
     * @param lvnList list of local variable nodes for method
     * @param insnList instruction list for method
     * @param insnNode instruction within method being searched against
     * @param idx local variable table index, or {@code null} if no local variable nodes are specified for {@code idx} and {@code insnNode}
     * combination
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if arguments aren't all from the same method, or if {@code idx < 0}
     * @return local variable node associated with the instruction
     */
    public static LocalVariableNode findLocalVariableNodeForInstruction(List<LocalVariableNode> lvnList, InsnList insnList,
            final AbstractInsnNode insnNode, int idx) {
        Validate.notNull(insnList);
        Validate.notNull(insnNode);
        Validate.isTrue(idx >= 0);
        
        int insnIdx = insnList.indexOf(insnNode);
        Validate.isTrue(insnIdx != -1);
        
        lvnList = lvnList.stream()
                .filter(lvn -> lvn.index == idx) // filter to lvns at the index we want
                .filter(lvn -> {                 // filter to lvns that's scope starts before the instruction we want
                    AbstractInsnNode currentInsnNode = insnNode.getPrevious();
                    while (currentInsnNode != null) {
                        if (currentInsnNode == lvn.start) {
                            return true;
                        }
                        currentInsnNode = currentInsnNode.getPrevious();
                    }
                    return false;
                })
                .filter(lvn -> {                 // filter to lvns that's scope stops after the instruction we want
                    AbstractInsnNode currentInsnNode = insnNode.getNext();
                    while (currentInsnNode != null) {
                        if (currentInsnNode == lvn.end) {
                            return true;
                        }
                        currentInsnNode = currentInsnNode.getNext();
                    }
                    return false;
                })
                .collect(Collectors.toList());
        
        
        // If we don't have any LVNs at this point, return null
        if (lvnList.isEmpty()) {
            return null;
        }

        
        // Should this be a list or should it always be a single entry? The problem is that there's nothing stopping multiple LVN's coming
        // back for some instruction+lvt_index combination.
        //
        // The one thing we can be sure of at this point is that IF WE GET BACK MULTIPLE LVNs, THEY MUST OVERLAP AT SOME POINT.
        
        // The assumption at this point is...
        //   1. LVNs are scoped such that the index of start label is BEFORE the index of the end label
        //   2. LVNs must fully overlap, meaning that they can't go past each other's boundaries
        //   3. LVNs can end at the same label, but they can't start at the same label
        //        e.g. not allowed
        //             x-----------x
        //                   x--------x
        //        e.g. allowed
        //             x-----------x
        //               x--------x
        //        e.g. allowed
        //              x--------x
        //             x-----------x
        //        e.g. not allowed
        //             x--------x
        //             x-----------x
        //
        // Error out if you spot this -- someone will eventually report it and it'll get fixed
        
            // the following blocks of code are far from efficient, but they're easily readable/understandable
        for (LocalVariableNode lvn : lvnList) { // test condition 1
            int start = insnList.indexOf(lvn.start);
            int end = insnList.indexOf(lvn.end);
            Validate.validState(end > start);
        }

        for (LocalVariableNode lvnTester : lvnList) { // test condition 2 and 3
            int startTester = insnList.indexOf(lvnTester.start);
            int endTester = insnList.indexOf(lvnTester.end);
            Range rangeTester = Range.between(startTester, endTester);
            
            for (LocalVariableNode lvnTestee : lvnList) {
                if (lvnTester == lvnTestee) {
                    continue;
                }
                
                int startTestee = insnList.indexOf(lvnTestee.start);
                int endTestee = insnList.indexOf(lvnTestee.end);
                Range rangeTestee = Range.between(startTestee, endTestee);
                
                Range intersectRange = rangeTester.intersectionWith(rangeTestee); 
                Validate.validState(intersectRange.equals(rangeTester) || intersectRange.equals(rangeTestee)); // test condition 2
                
                Validate.validState(rangeTester.getMinimum() != rangeTestee.getMinimum()); // test condition 3
            }
        }
        
        
        // Given that all the above assumptions are correct, the LVN with the smallest range will be the correct one. It's the one that's
        // most tightly scoped around the instruction.
        //   e.g.
        //    x------------i----x
        //        x--------i-x
        //             x---i-x
        
        return Collections.min(lvnList, (o1, o2) -> {
            int o1Len = insnList.indexOf(o1.end) - insnList.indexOf(o1.start);
            int o2Len = insnList.indexOf(o2.end) - insnList.indexOf(o2.start);
            return Integer.compare(o1Len, o2Len);
        });
    }

    /**
     * Find field within a class by its name.
     * @param classNode class to search
     * @param name name to search for
     * @return found field (or {@code null} if not found)
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is empty
     */
    public static FieldNode findField(ClassNode classNode, String name) {
        Validate.notNull(classNode);
        Validate.notNull(name);
        Validate.notEmpty(name);
        return classNode.fields.stream()
                .filter(x -> name.equals(x.name))
                .findAny().orElse(null);
    }
}
