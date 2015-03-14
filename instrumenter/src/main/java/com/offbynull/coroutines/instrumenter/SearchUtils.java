package com.offbynull.coroutines.instrumenter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Utility class to search bytecode in classes/methods/patterns for specific patterns. 
 * @author Kasra Faghihi
 */
public final class SearchUtils {

    private SearchUtils() {
        // do nothing
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
     * Find invocations of any method where the parameter list starts with a certain list of types (order matters).
     * @param insnList instruction list to search through
     * @param expectedStartingParamTypes starting parameter types
     * @return list of invocations (may be nodes of type {@link MethodInsnNode} or {@link InvokeDynamicInsnNode})
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if any element in {@code expectedStartingParamTypes} is either of sort {@link Type#METHOD}
     * or {@link Type#VOID}
     */
    public static List<AbstractInsnNode> findInvocationsThatStartWithParameters(InsnList insnList,
            Type... expectedStartingParamTypes) {
        Validate.notNull(insnList);
        Validate.notNull(expectedStartingParamTypes);
        Validate.noNullElements(expectedStartingParamTypes);
        for (Type type : expectedStartingParamTypes) {
            Validate.isTrue(type.getSort() != Type.METHOD && type.getSort() != Type.VOID);
        }

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

            if (doParametersStartWith(methodParamTypes, expectedStartingParamTypes)) {
                ret.add(instructionNode);
            }
        }

        return ret;
    }

    /**
     * Find methods within a class where the parameter list starts with a certain list of types (order matters).
     * @param methodNodes method nodes to search through
     * @param expectedStartingParamTypes starting parameter types
     * @return list of methods
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if any element in {@code expectedStartingParamTypes} is either of sort {@link Type#METHOD}
     * or {@link Type#VOID}
     */
    public static List<MethodNode> findMethodsThatStartWithParameters(Collection<MethodNode> methodNodes,
            Type... expectedStartingParamTypes) {
        Validate.notNull(methodNodes);
        Validate.notNull(expectedStartingParamTypes);
        Validate.noNullElements(methodNodes);
        Validate.noNullElements(expectedStartingParamTypes);
        for (Type type : expectedStartingParamTypes) {
            Validate.isTrue(type.getSort() != Type.METHOD && type.getSort() != Type.VOID);
        }

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

    private static boolean doParametersStartWith(Type[] types, Type[] expectedStartingTypes) {
        if (types.length < expectedStartingTypes.length) {
            return false;
        }

        List<Type> truncatedMethodParams = Arrays.asList(types).subList(0, expectedStartingTypes.length);
        List<Type> expectedParams = Arrays.asList(expectedStartingTypes);
        return expectedParams.equals(truncatedMethodParams);
    }

    public static Map<String, String> getSuperClassMappings(List<File> classPaths) throws IOException {
        Validate.notNull(classPaths);
        Validate.noNullElements(classPaths);
        
        Map<String, String> ret = new HashMap<>();
        for (File classPath : classPaths) {
            Validate.isTrue(classPath.exists());
            
            Map<String, String> superNameMappingsForPath;
            if (classPath.isFile()) {
                superNameMappingsForPath = openJarAndGetSuperClassMappings(classPath);
            } else {
                superNameMappingsForPath = openFolderAndGetSuperClassMappings(classPath);
            }
            
            // remove those we already have entries for before adding, because we don't want to override name mappings for names we already
            // have
            superNameMappingsForPath.keySet().removeAll(ret.keySet());
            ret.putAll(superNameMappingsForPath);
        }
        
        return ret;
    }
    
    private static Map<String, String> openFolderAndGetSuperClassMappings(File folder) throws IOException {
        Validate.isTrue(folder.isDirectory());
        
        Map<String, String> ret = new HashMap<>();
        for (File file : FileUtils.listFiles(folder, new String[] { "class" }, true)) {
            try (InputStream is = new FileInputStream(file)) {
                populateSuperClassMapping(is, ret);
            }
        }
        
        return ret;
    }

    private static Map<String, String> openJarAndGetSuperClassMappings(File file) throws IOException {
        Validate.isTrue(file.isFile());
        
        Map<String, String> ret = new HashMap<>();
        
        try (ZipFile zipFile = new ZipFile(file)) {
            Enumeration<ZipArchiveEntry> entryEnum = zipFile.getEntries();
            while (entryEnum.hasMoreElements()) {
                ZipArchiveEntry entry = entryEnum.nextElement();
                if (!entry.getName().endsWith(".class") || entry.isDirectory()) {
                    continue;
                }

                try (InputStream is = zipFile.getInputStream(entry)) {
                    populateSuperClassMapping(is, ret);
                }
            }
        }
        
        return ret;
    }

    
    private static void populateSuperClassMapping(final InputStream is, Map<String, String> superClassMap) throws IOException {
        ClassReader classReader = new ClassReader(is);
        String name = classReader.getClassName();
        String superName = classReader.getSuperName();
        
        superClassMap.put(name, superName);
    }
}
