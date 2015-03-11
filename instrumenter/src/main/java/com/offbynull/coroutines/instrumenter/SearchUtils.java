package com.offbynull.coroutines.instrumenter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

// ASM utilities class
public final class SearchUtils {

    private SearchUtils() {
        // do nothing
    }

    public static List<AbstractInsnNode> findInvocationsOf(InsnList instructionNodes,
            Type expectedMethodType) {
        Validate.notNull(instructionNodes);
        Validate.notNull(expectedMethodType);
        Validate.isTrue(expectedMethodType.getSort() == Type.METHOD);

        List<AbstractInsnNode> ret = new ArrayList<>();
        
        Iterator<AbstractInsnNode> it = instructionNodes.iterator();
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

    public static List<AbstractInsnNode> findInvocationsThatStartWithParameters(InsnList instructionNodes,
            Type... expectedStartingParamTypes) {
        Validate.notNull(instructionNodes);
        Validate.notNull(expectedStartingParamTypes);
        Validate.noNullElements(expectedStartingParamTypes);

        List<AbstractInsnNode> ret = new ArrayList<>();
        
        Iterator<AbstractInsnNode> it = instructionNodes.iterator();
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

            if (doParametersStartWith(methodParamTypes, expectedStartingParamTypes)) {
                ret.add(instructionNode);
            }
        }

        return ret;
    }

    public static List<MethodNode> findMethodsThatStartWithParameters(Collection<MethodNode> methodNodes,
            Type... expectedStartingParamTypes) {
        Validate.notNull(methodNodes);
        Validate.notNull(expectedStartingParamTypes);
        Validate.noNullElements(expectedStartingParamTypes);

        List<MethodNode> ret = new ArrayList<>();
        for (MethodNode methodNode : methodNodes) {
            Type methodDescType = Type.getType(methodNode.desc);
            Type[] methodParamTypes = methodDescType.getArgumentTypes();

            if (doParametersStartWith(methodParamTypes, expectedStartingParamTypes)) {
                ret.add(methodNode);
            }
        }

        return ret;
    }

    private static boolean doParametersStartWith(Type[] types, Type[] expectedStartingTypes) {
        if (types.length < expectedStartingTypes.length) {
            return false;
        }

        List<Type> truncatedMethodParams = Arrays.asList(types).subList(0, expectedStartingTypes.length);
        List<Type> expectedParams = Arrays.asList(expectedStartingTypes);
        return expectedParams.equals(truncatedMethodParams);
    }

}
