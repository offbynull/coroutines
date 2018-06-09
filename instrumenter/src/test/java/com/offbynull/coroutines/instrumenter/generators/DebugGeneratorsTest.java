package com.offbynull.coroutines.instrumenter.generators;

import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.findMethodsWithName;
import com.offbynull.coroutines.instrumenter.asm.VariableTable;
import com.offbynull.coroutines.instrumenter.generators.DebugGenerators.MarkerType;
import static com.offbynull.coroutines.instrumenter.generators.DebugGenerators.debugMarker;
import static com.offbynull.coroutines.instrumenter.generators.DebugGenerators.debugPrint;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.loadStringConst;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.merge;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.returnVoid;
import static com.offbynull.coroutines.instrumenter.testhelpers.TestUtils.createJarAndLoad;
import static com.offbynull.coroutines.instrumenter.testhelpers.TestUtils.readZipResourcesAsClassNodes;
import java.net.URLClassLoader;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public final class DebugGeneratorsTest {
    private static final String STUB_CLASSNAME = "SimpleStub";
    private static final String STUB_FILENAME = STUB_CLASSNAME + ".class";
    private static final String ZIP_RESOURCE_PATH = STUB_CLASSNAME + ".zip";
    private static final String STUB_METHOD_NAME = "fillMeIn";

    private ClassNode classNode;
    private MethodNode methodNode;
    
    @BeforeEach
    public void setUp() throws Exception {
        // Load class, get method
        classNode = readZipResourcesAsClassNodes(ZIP_RESOURCE_PATH).get(STUB_FILENAME);
        methodNode = findMethodsWithName(classNode.methods, STUB_METHOD_NAME).get(0);
    }

    @Test
    public void mustNotCrashOnMarker() throws Exception {
        // Augment signature
        methodNode.desc = Type.getMethodDescriptor(Type.VOID_TYPE, new Type[] { });
        
        // Initialize variable table
        VariableTable varTable = new VariableTable(classNode, methodNode);

        methodNode.instructions
                = merge(
                        // test marker of each type
                        debugMarker(MarkerType.NONE, "marker1"),
                        debugMarker(MarkerType.CONSTANT, "marker2"),
                        debugMarker(MarkerType.STDOUT, "marker3"),
                        returnVoid()
                );
        
        // Write to JAR file + load up in classloader -- then execute tests
        try (URLClassLoader cl = createJarAndLoad(classNode)) {
            Object obj = cl.loadClass(STUB_CLASSNAME).newInstance();
            MethodUtils.invokeMethod(obj, STUB_METHOD_NAME);
        }
    }

    @Test
    public void mustNotCrashOnDebugPrint() throws Exception {
        // Augment signature
        methodNode.desc = Type.getMethodDescriptor(Type.VOID_TYPE, new Type[] { });
        
        // Initialize variable table
        VariableTable varTable = new VariableTable(classNode, methodNode);

        methodNode.instructions
                = merge(
                        // test marker of each type
                        debugPrint(loadStringConst("marker1")),
                        returnVoid()
                );
        
        // Write to JAR file + load up in classloader -- then execute tests
        try (URLClassLoader cl = createJarAndLoad(classNode)) {
            Object obj = cl.loadClass(STUB_CLASSNAME).newInstance();
            MethodUtils.invokeMethod(obj, STUB_METHOD_NAME);
        }
    }
}
