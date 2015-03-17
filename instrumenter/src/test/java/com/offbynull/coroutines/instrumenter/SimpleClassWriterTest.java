/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.offbynull.coroutines.instrumenter;

import static com.offbynull.coroutines.instrumenter.InstructionUtils.addLabel;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.call;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.construct;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.jumpTo;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.loadVar;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.merge;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.pop;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.saveVar;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.tableSwitch;
import static com.offbynull.coroutines.instrumenter.InstructionUtils.throwException;
import static com.offbynull.coroutines.instrumenter.SearchUtils.getSuperClassMappings;
import static com.offbynull.coroutines.instrumenter.TestUtils.getClasspath;
import static com.offbynull.coroutines.instrumenter.TestUtils.readZipFromResource;
import com.offbynull.coroutines.instrumenter.VariableTable.Variable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

public final class SimpleClassWriterTest {
    
    private ClassNode classNode;
    private MethodNode methodNode;
    
    @Before
    public void setUp() throws IOException {
        byte[] classData = readZipFromResource("SimpleStub.zip").get("SimpleStub.class");
        
        ClassReader classReader = new ClassReader(classData);
        classNode = new ClassNode();
        classReader.accept(classNode, 0);
        
        methodNode = classNode.methods.get(1); // stub should be here
    }

    @Test
    public void testSomeMethod() throws IOException {
        // augment method to take in a single int argument
        methodNode.desc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.INT_TYPE);
        
        
        // augment method instructions
        VariableTable varTable = new VariableTable(classNode, methodNode);
        
        Class<?> listClass = List.class;
        Constructor arrayListConstructor = ConstructorUtils.getAccessibleConstructor(ArrayList.class);
        Constructor linkedListConstructor = ConstructorUtils.getAccessibleConstructor(LinkedList.class);
        Method isEmptyMethod = MethodUtils.getAccessibleMethod(listClass, "isEmpty");
        
        Type listType = Type.getType(listClass);
        
        Variable testArg = varTable.getArgument(1);
        Variable listVar = varTable.acquireExtra(listType);
        
        /**
         * List list;
         * switch(arg1) {
         *     case 0:
         *         break;
         *     case 1:
         *         break;
         *     default: throw new RuntimeException("must be 0 or 1");
         * }
         * list.toString();
         */
        LabelNode invokePoint = new LabelNode();
        InsnList methodInsnList
                = merge(
                        tableSwitch(
                                loadVar(testArg),
                                throwException("must be 0 or 1"),
                                0,
                                merge(
                                        construct(arrayListConstructor),
                                        saveVar(listVar),
                                        jumpTo(invokePoint)
                                ),
                                merge(
                                        construct(linkedListConstructor),
                                        saveVar(listVar),
                                        jumpTo(invokePoint)
                                )
                        ),
                        addLabel(invokePoint),
                        call(isEmptyMethod, InstructionUtils.loadVar(listVar)),
                        pop() // discard results of call
                );
        
        methodNode.instructions = methodInsnList;
        
        
        // attemp to write out class -- since we're branching like this  it should call getCommonSuperClass() which is the logic we
        // explictly override and are testing
        List<File> classPaths = getClasspath();
        Map<String, String> superClassMappings = getSuperClassMappings(classPaths);
        
        SimpleClassWriter classWriter = new SimpleClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, superClassMappings);
        classNode.accept(classWriter); // should not crash after this line
    }
    
}
