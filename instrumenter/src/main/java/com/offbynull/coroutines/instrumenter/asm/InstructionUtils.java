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

import com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

/**
 * Utility class to generate common bytecode instructions.
 * @author Kasra Faghihi
 */
public final class InstructionUtils {

    private InstructionUtils() {
        // do nothing
    }
    
    /**
     * Returns an empty instruction list.
     * @return empty instruction list
     */
    public static InsnList empty() {
        return new InsnList();
    }

    /**
     * Clones an invokevirtual/invokespecial/invokeinterface/invokedynamic node and returns it as an instruction list.
     * @param insnNode instruction to clone
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if node isn't of invoke type
     * @return instruction list with cloned instruction
     */
    public static InsnList cloneInvokeNode(AbstractInsnNode insnNode) {
        Validate.notNull(insnNode);
        Validate.isTrue(insnNode instanceof MethodInsnNode || insnNode instanceof InvokeDynamicInsnNode);

        InsnList ret = new InsnList();
        ret.add(insnNode.clone(new HashMap<>()));

        return ret;
    }

    /**
     * Clones a monitorenter/monitorexit node and returns it as an instruction list.
     * @param insnNode instruction to clone
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if node isn't of invoke type
     * @return instruction list with cloned instruction
     */
    public static InsnList cloneMonitorNode(AbstractInsnNode insnNode) {
        Validate.notNull(insnNode);
        Validate.isTrue(insnNode instanceof InsnNode);
        Validate.isTrue(insnNode.getOpcode() == Opcodes.MONITORENTER || insnNode.getOpcode() == Opcodes.MONITOREXIT);

        InsnList ret = new InsnList();
        ret.add(insnNode.clone(new HashMap<>()));

        return ret;
    }

    /**
     * Clones an instruction list. Equivalent to calling {@code cloneInsnList(insnList, Collections.emptySet())}.
     * @param insnList instruction list to clone
     * @throws NullPointerException if any argument is {@code null}
     * @return instruction list with cloned instructions
     */
    public static InsnList cloneInsnList(InsnList insnList) {
        return cloneInsnList(insnList, Collections.emptySet());
    }
    
    /**
     * Clones an instruction list. All labels are remapped unless otherwise specified in {@code globalLabels}.
     * @param insnList instruction list to clone
     * @param globalLabels set of labels that should not be remapped
     * @throws NullPointerException if any argument is {@code null}
     * @return instruction list with cloned instructions
     */
    public static InsnList cloneInsnList(InsnList insnList, Set<LabelNode> globalLabels) {
        Validate.notNull(insnList);

        // remap all labelnodes
        Map<LabelNode, LabelNode> labelNodeMapping = new HashMap<>();
        ListIterator<AbstractInsnNode> it = insnList.iterator();
        while (it.hasNext()) {
            AbstractInsnNode abstractInsnNode = it.next();
            if (abstractInsnNode instanceof LabelNode) {
                LabelNode existingLabelNode = (LabelNode) abstractInsnNode;
                labelNodeMapping.put(existingLabelNode, new LabelNode());
            }
        }
        
        // override remapping such that global labels stay the same
        for (LabelNode globalLabel : globalLabels) {
            labelNodeMapping.put(globalLabel, globalLabel);
        }
        
        // clone
        InsnList ret = new InsnList();
        it = insnList.iterator();
        while (it.hasNext()) {
            AbstractInsnNode abstractInsnNode = it.next();
            ret.add(abstractInsnNode.clone(labelNodeMapping));
        }

        return ret;
    }
    
    /**
     * Combines multiple instruction lists in to a single instruction list.
     * @param insnLists instruction lists to merge
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     * @return merged instructions
     */
    public static InsnList merge(InsnList... insnLists) {
        Validate.notNull(insnLists);
        Validate.noNullElements(insnLists);

        InsnList ret = new InsnList();
        for (InsnList insnList : insnLists) {
            ret.add(insnList);
        }

        return ret;
    }

    /**
     * Generates instructions for an unconditional jump to a label.
     * @param labelNode label to jump to
     * @throws NullPointerException if any argument is {@code null}
     * @return instructions for an unconditional jump to {@code labelNode}
     */
    public static InsnList jumpTo(LabelNode labelNode) {
        Validate.notNull(labelNode);

        InsnList ret = new InsnList();
        ret.add(new JumpInsnNode(Opcodes.GOTO, labelNode));

        return ret;
    }
    
    /**
     * Generates instructions for a label.
     * @param labelNode label to insert
     * @throws NullPointerException if any argument is {@code null}
     * @return instructions for a label
     */
    public static InsnList addLabel(LabelNode labelNode) {
        Validate.notNull(labelNode);

        InsnList ret = new InsnList();
        ret.add(labelNode);

        return ret;
    }
    
    /**
     * Generates instructions for line numbers. This is useful for debugging. For example, you can put a line number of 99999 or some other
     * special number to denote that the code being executed is instrumented code. Then if a stacktrace happens, you'll know that if
     * instrumented code was immediately involved.
     * @param num line number
     * @return instructions for a line number
     * @throws IllegalArgumentException if {@code num < 0}
     */
    public static InsnList lineNumber(int num) {
        Validate.isTrue(num >= 0);
        
        InsnList ret = new InsnList();
        
        LabelNode labelNode = new LabelNode();
        ret.add(labelNode);
        ret.add(new LineNumberNode(num, labelNode));

        return ret;
    }

    /**
     * Generates instructions for printing out a string constant using {@link System#out}. This is useful for debugging. For example, you
     * can print out lines around your instrumented code to make sure that what you think is being run is actually being run.
     * @param text text to print out
     * @return instructions to call System.out.println with a string constant
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList debugPrint(String text) {
        Validate.notNull(text);
        
        InsnList ret = new InsnList();
        
        ret.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
        ret.add(new LdcInsnNode(text));
        ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false));

        return ret;
    }
    
    /**
     * Generates instructions to pop an item off the stack.
     * @return instructions for a pop
     */
    public static InsnList pop() {
        InsnList ret = new InsnList();
        ret.add(new InsnNode(Opcodes.POP));

        return ret;
    }

    /**
     * Generates instructions to pop {@code count} items off the stack.
     * @param count number of items to pop
     * @return instructions for a pop
     * @throws IllegalArgumentException if any numeric argument is negative
     */
    public static InsnList pop(int count) {
        Validate.isTrue(count >= 0);
        InsnList ret = new InsnList();
        for (int i = 0; i < count; i++) {
            ret.add(new InsnNode(Opcodes.POP));
        }

        return ret;
    }

    /**
     * Generates a MONITORENTER instruction, which consumes an Object from the top of the stack.
     * @return instructions for a pop
     */
    public static InsnList monitorEnter() {
        InsnList ret = new InsnList();
        ret.add(new InsnNode(Opcodes.MONITORENTER));

        return ret;
    }

    /**
     * Generates a MONITOREXIT instruction, which consumes an Object from the top of the stack.
     * @return instructions for a pop
     */
    public static InsnList monitorExit() {
        InsnList ret = new InsnList();
        ret.add(new InsnNode(Opcodes.MONITOREXIT));

        return ret;
    }

    /**
     * Generates instructions to push an integer constant on to the stack.
     * @param i integer constant to push
     * @return instructions to push an integer constant
     */
    public static InsnList loadIntConst(int i) {
        InsnList ret = new InsnList();
        ret.add(new LdcInsnNode(i));
        return ret;
    }

    /**
     * Generates instruction to push a string constant on to the stack.
     * @param s string constant to push
     * @return instructions to push a string constant
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList loadStringConst(String s) {
        Validate.notNull(s);
        InsnList ret = new InsnList();
        ret.add(new LdcInsnNode(s));
        return ret;
    }

    /**
     * Generates instruction to push a null on to the stack.
     * @return instructions to push a null
     */
    public static InsnList loadNull() {
        InsnList ret = new InsnList();
        ret.add(new InsnNode(Opcodes.ACONST_NULL));
        return ret;
    }
    
    /**
     * Copies a local variable on to the stack.
     * @param variable variable within the local variable table to load from
     * @return instructions to load a local variable on to the stack
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code variable} has been released
     */
    public static InsnList loadVar(Variable variable) {
        Validate.notNull(variable);

        InsnList ret = new InsnList();
        switch (variable.getType().getSort()) {
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
                ret.add(new VarInsnNode(Opcodes.ILOAD, variable.getIndex()));
                break;
            case Type.LONG:
                ret.add(new VarInsnNode(Opcodes.LLOAD, variable.getIndex()));
                break;
            case Type.FLOAT:
                ret.add(new VarInsnNode(Opcodes.FLOAD, variable.getIndex()));
                break;
            case Type.DOUBLE:
                ret.add(new VarInsnNode(Opcodes.DLOAD, variable.getIndex()));
                break;
            case Type.OBJECT:
            case Type.ARRAY:
                ret.add(new VarInsnNode(Opcodes.ALOAD, variable.getIndex()));
                ret.add(new TypeInsnNode(Opcodes.CHECKCAST, variable.getType().getInternalName()));
                break;
            default:
                throw new IllegalStateException(); // should never happen, there is code in Variable/VariableTable to make sure invalid
                                                   // types aren't set
        }

        return ret;
    }

    /**
     * Pops the stack in to the the local variable table. You may run in to problems if the item on top of the stack isn't of the same type
     * as the variable it's being put in to.
     * @param variable variable within the local variable table to save to
     * @return instructions to pop an item off the top of the stack and save it to {@code variable}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code variable} has been released
     */
    public static InsnList saveVar(Variable variable) {
        Validate.notNull(variable);

        InsnList ret = new InsnList();
        switch (variable.getType().getSort()) {
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
                ret.add(new VarInsnNode(Opcodes.ISTORE, variable.getIndex()));
                break;
            case Type.LONG:
                ret.add(new VarInsnNode(Opcodes.LSTORE, variable.getIndex()));
                break;
            case Type.FLOAT:
                ret.add(new VarInsnNode(Opcodes.FSTORE, variable.getIndex()));
                break;
            case Type.DOUBLE:
                ret.add(new VarInsnNode(Opcodes.DSTORE, variable.getIndex()));
                break;
            case Type.OBJECT:
            case Type.ARRAY:
                ret.add(new VarInsnNode(Opcodes.ASTORE, variable.getIndex()));
                break;
            default:
                throw new IllegalStateException(); // should never happen, there is code in Variable/VariableTable to make sure invalid
                                                   // types aren't set
        }

        return ret;
    }
    
    /**
     * Calls a constructor with a set of arguments. After execution the stack should have an extra item pushed on it: the object that was
     * created by this constructor.
     * @param constructor constructor to call
     * @param args constructor argument instruction lists -- each instruction list must leave one item on the stack of the type expected
     * by the constructor
     * @return instructions to invoke a constructor
     * @throws NullPointerException if any argument is {@code null} or array contains {@code null}
     * @throws IllegalArgumentException if the length of {@code args} doesn't match the number of parameters in {@code constructor}
     */
    public static InsnList construct(Constructor<?> constructor, InsnList ... args) {
        Validate.notNull(constructor);
        Validate.notNull(args);
        Validate.noNullElements(args);
        Validate.isTrue(constructor.getParameterCount() == args.length);
        
        
        InsnList ret = new InsnList();
        
        Type clsType = Type.getType(constructor.getDeclaringClass());
        Type methodType = Type.getType(constructor);
        
        ret.add(new TypeInsnNode(Opcodes.NEW, clsType.getInternalName()));
        ret.add(new InsnNode(Opcodes.DUP));
        for (InsnList arg : args) {
            ret.add(arg);
        }
        ret.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, clsType.getInternalName(), "<init>", methodType.getDescriptor(), false));
        
        return ret;
    }

    /**
     * Compares two integers and performs some action if the integers are equal.
     * @param lhs left hand side instruction list -- must leave an int on top of the stack
     * @param rhs right hand side instruction list -- must leave an int on top of the stack
     * @param action action to perform if results of {@code lhs} and {@code rhs} are equal
     * @return instructions instruction list to perform some action if two ints are equal
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList ifIntegersEqual(InsnList lhs, InsnList rhs, InsnList action) {
        Validate.notNull(lhs);
        Validate.notNull(rhs);
        Validate.notNull(action);
        
        
        InsnList ret = new InsnList();
        
        LabelNode notEqualLabelNode = new LabelNode();
        
        ret.add(lhs);
        ret.add(rhs);
        ret.add(new JumpInsnNode(Opcodes.IF_ICMPNE, notEqualLabelNode));
        ret.add(action);
        ret.add(notEqualLabelNode);
        
        return ret;
    }

/**
     * Compares two objects and performs some action if the objects are the same (uses == to check if same, not the equals method).
     * @param lhs left hand side instruction list -- must leave an object on top of the stack
     * @param rhs right hand side instruction list -- must leave an object on top of the stack
     * @param action action to perform if results of {@code lhs} and {@code rhs} are equal
     * @return instructions instruction list to perform some action if two objects are equal
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList ifObjectsEqual(InsnList lhs, InsnList rhs, InsnList action) {
        Validate.notNull(lhs);
        Validate.notNull(rhs);
        Validate.notNull(action);
        
        
        InsnList ret = new InsnList();
        
        LabelNode notEqualLabelNode = new LabelNode();
        
        ret.add(lhs);
        ret.add(rhs);
        ret.add(new JumpInsnNode(Opcodes.IF_ACMPNE, notEqualLabelNode));
        ret.add(action);
        ret.add(notEqualLabelNode);
        
        return ret;
    }

    /**
     * For each element in an object array, performs an action.
     * @param counterVar parameter used to keep track of count in loop
     * @param arrayLenVar parameter used to keep track of array length
     * @param array object array instruction list -- must leave an array on top of the stack
     * @param action action to perform on each element -- element will be at top of stack and must be consumed by these instructions
     * @return instructions instruction list to perform some action if two ints are equal
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList forEach(Variable counterVar, Variable arrayLenVar, InsnList array, InsnList action) {
        Validate.notNull(counterVar);
        Validate.notNull(arrayLenVar);
        Validate.notNull(array);
        Validate.notNull(action);
        Validate.isTrue(counterVar.getType().equals(Type.INT_TYPE));
        Validate.isTrue(arrayLenVar.getType().equals(Type.INT_TYPE));
        
        
        InsnList ret = new InsnList();
        
        LabelNode doneLabelNode = new LabelNode();
        LabelNode loopLabelNode = new LabelNode();
        
        // put zero in to counterVar
        ret.add(new LdcInsnNode(0)); // int
        ret.add(new VarInsnNode(Opcodes.ISTORE, counterVar.getIndex())); //
        
        // load array we'll be traversing over
        ret.add(array); // object[]
        
        // put array length in to arrayLenVar
        ret.add(new InsnNode(Opcodes.DUP)); // object[], object[]
        ret.add(new InsnNode(Opcodes.ARRAYLENGTH)); // object[], int
        ret.add(new VarInsnNode(Opcodes.ISTORE, arrayLenVar.getIndex())); // object[]
        
        // loopLabelNode: test if counterVar == arrayLenVar, if it does then jump to doneLabelNode
        ret.add(loopLabelNode);
        ret.add(new VarInsnNode(Opcodes.ILOAD, counterVar.getIndex())); // object[], int
        ret.add(new VarInsnNode(Opcodes.ILOAD, arrayLenVar.getIndex())); // object[], int, int
        ret.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, doneLabelNode)); // object[]
        
        // load object from object[]
        ret.add(new InsnNode(Opcodes.DUP)); // object[], object[]
        ret.add(new VarInsnNode(Opcodes.ILOAD, counterVar.getIndex())); // object[], object[], int
        ret.add(new InsnNode(Opcodes.AALOAD)); // object[], object
        
        // call action
        ret.add(action); // object[]
        
        // increment counter var and goto loopLabelNode
        ret.add(new IincInsnNode(counterVar.getIndex(), 1)); // object[]
        ret.add(new JumpInsnNode(Opcodes.GOTO, loopLabelNode)); // object[]
        
        // doneLabelNode: pop object[] off of stack
        ret.add(doneLabelNode);
        ret.add(new InsnNode(Opcodes.POP)); //
        
        return ret;
    }

    /**
     * Calls a method with a set of arguments. After execution the stack may have an extra item pushed on it: the object that was returned
     * by this method (if any).
     * @param method method to call
     * @param args method argument instruction lists -- each instruction list must leave one item on the stack of the type expected
     * by the method (note that if this is a non-static method, the first argument must always evaluate to the "this" pointer/reference)
     * @return instructions to invoke a method
     * @throws NullPointerException if any argument is {@code null} or array contains {@code null}
     * @throws IllegalArgumentException if the length of {@code args} doesn't match the number of parameters in {@code method}
     */
    public static InsnList call(Method method, InsnList ... args) {
        Validate.notNull(method);
        Validate.notNull(args);
        Validate.noNullElements(args);
        
        
        InsnList ret = new InsnList();
        
        for (InsnList arg : args) {
            ret.add(arg);
        }
        
        Type clsType = Type.getType(method.getDeclaringClass());
        Type methodType = Type.getType(method);
        
        if ((method.getModifiers() & Modifier.STATIC) == Modifier.STATIC) {
            Validate.isTrue(method.getParameterCount() == args.length);
            ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, clsType.getInternalName(), method.getName(), methodType.getDescriptor(),
                    false));
        } else if (method.getDeclaringClass().isInterface()) {
            Validate.isTrue(method.getParameterCount() + 1 == args.length);
            ret.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, clsType.getInternalName(), method.getName(), methodType.getDescriptor(),
                    true));
        } else {
            Validate.isTrue(method.getParameterCount() + 1 == args.length);
            ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, clsType.getInternalName(), method.getName(), methodType.getDescriptor(),
                    false));
        }
        
        return ret;
    }

    /**
     * Generates instructions to throw an exception of type {@link RuntimeException} with a constant message.
     * @param message message of exception
     * @return instructions to throw an exception
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList throwException(String message) {
        Validate.notNull(message);

        InsnList ret = new InsnList();

        ret.add(new TypeInsnNode(Opcodes.NEW, "java/lang/RuntimeException"));
        ret.add(new InsnNode(Opcodes.DUP));
        ret.add(new LdcInsnNode(message));
        ret.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false));
        ret.add(new InsnNode(Opcodes.ATHROW));

        return ret;
    }
    
    /**
     * Generates instructions for a switch table. This does not automatically generate jumps at the end of each default/case statement. It's
     * your responsibility to either add the relevant jumps, throws, or returns at each default/case statement, otherwise the code will
     * just fall through (which is likely not what you want).
     * @param indexInsnList instructions to calculate the index -- must leave an int on top of the stack
     * @param defaultInsnList instructions to execute on default statement -- must leave the stack unchanged
     * @param caseStartIdx the number which the case statements start at
     * @param caseInsnLists instructions to execute on each case statement -- must leave the stack unchanged
     * @return instructions for a table switch
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if any numeric argument is {@code < 0}, or if {@code caseInsnLists} is empty
     */
    public static InsnList tableSwitch(InsnList indexInsnList, InsnList defaultInsnList, int caseStartIdx, InsnList... caseInsnLists) {
        Validate.notNull(defaultInsnList);
        Validate.notNull(indexInsnList);
        Validate.isTrue(caseStartIdx >= 0);
        Validate.notNull(caseInsnLists);
        Validate.noNullElements(caseInsnLists);
        Validate.isTrue(caseInsnLists.length > 0);
        InsnList ret = new InsnList();

        LabelNode defaultLabelNode = new LabelNode();
        LabelNode[] caseLabelNodes = new LabelNode[caseInsnLists.length];

        for (int i = 0; i < caseInsnLists.length; i++) {
            caseLabelNodes[i] = new LabelNode();
        }

        ret.add(indexInsnList);
        ret.add(new TableSwitchInsnNode(caseStartIdx, caseStartIdx + caseInsnLists.length - 1, defaultLabelNode, caseLabelNodes));

        for (int i = 0; i < caseInsnLists.length; i++) {
            LabelNode caseLabelNode = caseLabelNodes[i];
            InsnList caseInsnList = caseInsnLists[i];
            if (caseInsnList != null) {
                ret.add(caseLabelNode);
                ret.add(caseInsnList);
            }
        }

        if (defaultInsnList != null) {
            ret.add(defaultLabelNode);
            ret.add(defaultInsnList);
        }
        
        return ret;
    }

    /**
     * Generates instructions for a try-catch block.
     * @param tryCatchBlockNode try catch block node to populate to with label with relevant information
     * @param exceptionType exception type to catch ({@code null} means catch any exception)
     * @param tryInsnList instructions to execute for try block
     * @param catchInsnList instructions to execute for catch block
     * @return instructions for a try catch block
     * @throws NullPointerException if any argument other than {@code exceptionType} is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if {@code exceptionType} is not an object type (technically must inherit from {@link Throwable},
     * but no way to check this)
     */
    public static InsnList tryCatchBlock(TryCatchBlockNode tryCatchBlockNode, Type exceptionType, InsnList tryInsnList,
            InsnList catchInsnList) {
        Validate.notNull(tryInsnList);
        // exceptionType can be null
        Validate.notNull(catchInsnList);
        if (exceptionType != null) {
            Validate.isTrue(exceptionType.getSort() == Type.OBJECT);
        }
        
        InsnList ret = new InsnList();

        LabelNode tryLabelNode = new LabelNode();
        LabelNode catchLabelNode = new LabelNode();
        LabelNode endLabelNode = new LabelNode();
        
        tryCatchBlockNode.start = tryLabelNode;
        tryCatchBlockNode.end = catchLabelNode;
        tryCatchBlockNode.handler = catchLabelNode;
        tryCatchBlockNode.type = exceptionType == null ? null : exceptionType.getInternalName();

        ret.add(tryLabelNode);
        ret.add(tryInsnList);
        ret.add(new JumpInsnNode(Opcodes.GOTO, endLabelNode));
        ret.add(catchLabelNode);
        ret.add(catchInsnList);
        ret.add(endLabelNode);
        return ret;
    }

    /**
     * Generates instructions to load the operand stack from an object array.
     * @param arrayStackVar variable that the object array containing operand stack is stored
     * @param tempObjectVar variable to use for temporary objects
     * @param frame execution frame at the instruction for which the operand stack is to be restored
     * @return instructions to load the operand stack from an array
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if variables have the same index, or if variables have been released, or if variables are of wrong
     * type
     */
    public static InsnList loadOperandStack(Variable arrayStackVar, Variable tempObjectVar, Frame<BasicValue> frame) {
        return loadOperandStack(arrayStackVar, tempObjectVar, frame, 0, frame.getStackSize());
    }

    /**
     * Generates instructions to load the last {@code count} items of the operand stack from an object array. The object array contains all
     * items for the stack, but only the tail {@code count} items will be loaded on to the stack.
     * @param arrayStackVar variable that the object array containing operand stack is stored
     * @param tempObjectVar variable to use for temporary objects
     * @param frame execution frame at the instruction for which the operand stack is to be restored
     * @param count number of items to load to the bottom of the stack.
     * @return instructions to load the relevant portion of the operand stack from an array
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if variables have the same index, or if variables have been released, or if variables are of wrong
     * type, or if there aren't {@code count} items on the stack
     */
    public static InsnList loadOperandStackSuffix(Variable arrayStackVar, Variable tempObjectVar, Frame<BasicValue> frame, int count) {
        int start = frame.getStackSize() - count;
        int end = frame.getStackSize();
        Validate.isTrue(start >= 0);
        return loadOperandStack(arrayStackVar, tempObjectVar, frame, start, end);
    }

    /**
     * Generates instructions to load the first {@code count} items of the operand stack from an object array. The object array contains all
     * items for the stack, but only the beginning {@code count} items will be loaded on to the stack.
     * @param arrayStackVar variable that the object array containing operand stack is stored
     * @param tempObjectVar variable to use for temporary objects
     * @param frame execution frame at the instruction for which the operand stack is to be restored
     * @param count number of items to load to the bottom of the stack.
     * @return instructions to load the relevant portion of operand stack from an array
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if variables have the same index, or if variables have been released, or if variables are of wrong
     * type, or if there aren't {@code count} items on the stack
     */
    public static InsnList loadOperandStackPrefix(Variable arrayStackVar, Variable tempObjectVar, Frame<BasicValue> frame, int count) {
        int start = 0;
        int end = count;
        Validate.isTrue(end <= frame.getStackSize());
        return loadOperandStack(arrayStackVar, tempObjectVar, frame, start, end);
    }
    
    private static InsnList loadOperandStack(Variable arrayStackVar, Variable tempObjectVar, Frame<BasicValue> frame, int start, int end) {
        Validate.notNull(arrayStackVar);
        Validate.notNull(tempObjectVar);
        Validate.notNull(frame);
        Validate.isTrue(arrayStackVar.getType().equals(Type.getType(Object[].class)));
        Validate.isTrue(tempObjectVar.getType().equals(Type.getType(Object.class)));
        validateLocalIndicies(arrayStackVar.getIndex(), tempObjectVar.getIndex());
        Validate.isTrue(start >= 0);
        Validate.isTrue(end >= start); // end is exclusive
        Validate.isTrue(end <= frame.getStackSize());
        
        InsnList ret = new InsnList();
        
        // Restore the stack
        for (int i = start; i < end; i++) {
            BasicValue basicValue = frame.getStack(i);
            Type type = basicValue.getType();
            
            // If type is 'Lnull;', this means that the slot has been assigned null and that "there has been no merge yet that would 'raise'
            // the type toward some class or interface type" (from ASM mailing list). We know this slot will always contain null at this
            // point in the code so there's no specific value to load up from the array. Instead we push a null in to that slot, thereby
            // keeping the same 'Lnull;' type originally assigned to that slot (it doesn't make sense to do a CHECKCAST because 'null' is
            // not a real class and can never be a real class -- null is a reserved word in Java).
            if (type.getSort() == Type.OBJECT && "Lnull;".equals(type.getDescriptor())) {
                ret.add(new InsnNode(Opcodes.ACONST_NULL));
                continue;
            }

            // Load item from stack storage array
            ret.add(new VarInsnNode(Opcodes.ALOAD, arrayStackVar.getIndex()));
            ret.add(new LdcInsnNode(i));
            ret.add(new InsnNode(Opcodes.AALOAD));

            // Convert the item to an object (if not already an object) and stores it in local vars table. Item removed from stack.
            switch (type.getSort()) {
                case Type.BOOLEAN:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Boolean"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false));
                    break;
                case Type.BYTE:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Byte"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false));
                    break;
                case Type.SHORT:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Short"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false));
                    break;
                case Type.CHAR:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Character"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false));
                    break;
                case Type.INT:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Integer"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false));
                    break;
                case Type.FLOAT:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Float"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false));
                    break;
                case Type.LONG:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Long"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false));
                    break;
                case Type.DOUBLE:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Double"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false));
                    break;
                case Type.ARRAY:
                case Type.OBJECT:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, basicValue.getType().getInternalName()));
                    break;
                case Type.METHOD:
                case Type.VOID:
                default:
                    throw new IllegalArgumentException();
            }
        }

        return ret;
    }
    
    /**
     * Generates instructions to save the operand stack to an object array.
     * @param arrayStackVar variable that the object array containing operand stack is stored
     * @param tempObjectVar variable to use for temporary objects
     * @param frame execution frame at the instruction where the operand stack is to be saved
     * @return instructions to save the operand stack in to an array and save it to the local variables table
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if variables have the same index, or if variables have been released, or if variables are of wrong
     * type
     */
    public static InsnList saveOperandStack(Variable arrayStackVar, Variable tempObjectVar, Frame<BasicValue> frame) {
        Validate.notNull(arrayStackVar);
        Validate.notNull(tempObjectVar);
        Validate.notNull(frame);
        Validate.isTrue(arrayStackVar.getType().equals(Type.getType(Object[].class)));
        Validate.isTrue(tempObjectVar.getType().equals(Type.getType(Object.class)));
        validateLocalIndicies(arrayStackVar.getIndex(), tempObjectVar.getIndex());
        
        InsnList ret = new InsnList();

        // Create stack storage array and save it in local vars table
        ret.add(new LdcInsnNode(frame.getStackSize()));
        ret.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        ret.add(new VarInsnNode(Opcodes.ASTORE, arrayStackVar.getIndex()));

        // Save the stack
        for (int i = frame.getStackSize() - 1; i >= 0; i--) {
            BasicValue basicValue = frame.getStack(i);
            Type type = basicValue.getType();
            
            // If type is 'Lnull;', this means that the slot has been assigned null and that "there has been no merge yet that would 'raise'
            // the type toward some class or interface type" (from ASM mailing list). We know this slot will always contain null at this
            // point in the code so we can avoid saving it (but we still need to do a POP to get rid of it). When we load it back up, we can
            // simply push a null in to that slot, thereby keeping the same 'Lnull;' type.
            if ("Lnull;".equals(type.getDescriptor())) {
                ret.add(new InsnNode(Opcodes.POP));
                continue;
            }

            // Convert the item to an object (if not already an object) and stores it in local vars table. Item removed from stack.
            switch (type.getSort()) {
                case Type.BOOLEAN:
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;"));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectVar.getIndex()));
                    break;
                case Type.BYTE:
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectVar.getIndex()));
                    break;
                case Type.SHORT:
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectVar.getIndex()));
                    break;
                case Type.CHAR:
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectVar.getIndex()));
                    break;
                case Type.INT:
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectVar.getIndex()));
                    break;
                case Type.FLOAT:
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectVar.getIndex()));
                    break;
                case Type.LONG:
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectVar.getIndex()));
                    break;
                case Type.DOUBLE:
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectVar.getIndex()));
                    break;
                case Type.ARRAY:
                case Type.OBJECT:
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectVar.getIndex()));
                    break;
                case Type.METHOD:
                case Type.VOID:
                default:
                    throw new IllegalArgumentException();
            }

            // Store item in to stack storage array
            ret.add(new VarInsnNode(Opcodes.ALOAD, arrayStackVar.getIndex()));
            ret.add(new LdcInsnNode(i));
            ret.add(new VarInsnNode(Opcodes.ALOAD, tempObjectVar.getIndex()));
            ret.add(new InsnNode(Opcodes.AASTORE));
        }

        // Restore the stack
        for (int i = 0; i < frame.getStackSize(); i++) {
            BasicValue basicValue = frame.getStack(i);
            Type type = basicValue.getType();
            
            // If type is 'Lnull;', this means that the slot has been assigned null and that "there has been no merge yet that would 'raise'
            // the type toward some class or interface type" (from ASM mailing list). We know this slot will always contain null at this
            // point in the code so there's no specific value to load up from the array. Instead we push a null in to that slot, thereby
            // keeping the same 'Lnull;' type originally assigned to that slot (it doesn't make sense to do a CHECKCAST because 'null' is
            // not a real class and can never be a real class -- null is a reserved word in Java).
            if (type.getSort() == Type.OBJECT && "Lnull;".equals(type.getDescriptor())) {
                ret.add(new InsnNode(Opcodes.ACONST_NULL));
                continue;
            }

            // Load item from stack storage array
            ret.add(new VarInsnNode(Opcodes.ALOAD, arrayStackVar.getIndex()));
            ret.add(new LdcInsnNode(i));
            ret.add(new InsnNode(Opcodes.AALOAD));

            // Convert the item to an object (if not already an object) and stores it in local vars table. Item removed from stack.
            switch (type.getSort()) {
                case Type.BOOLEAN:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Boolean"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false));
                    break;
                case Type.BYTE:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Byte"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false));
                    break;
                case Type.SHORT:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Short"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false));
                    break;
                case Type.CHAR:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Character"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false));
                    break;
                case Type.INT:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Integer"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false));
                    break;
                case Type.FLOAT:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Float"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false));
                    break;
                case Type.LONG:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Long"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false));
                    break;
                case Type.DOUBLE:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Double"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false));
                    break;
                case Type.ARRAY:
                case Type.OBJECT:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, basicValue.getType().getInternalName()));
                    break;
                case Type.METHOD:
                case Type.VOID:
                default:
                    throw new IllegalArgumentException();
            }
        }

        return ret;
    }

    /**
     * Generates instructions to load the local variables table from an object array.
     *
     * @param arrayLocalsVar variable that the object array containing local variables table is stored
     * @param tempObjectVar variable to use for temporary objects
     * @param frame execution frame at the instruction for which the local variables table is to be restored
     * @return instructions to load the local variables table from an array
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if variables have the same index, or if variables have been released, or if variables are of wrong
     * type
     */
    public static InsnList loadLocalVariableTable(Variable arrayLocalsVar, Variable tempObjectVar, Frame<BasicValue> frame) {
        Validate.notNull(arrayLocalsVar);
        Validate.notNull(tempObjectVar);
        Validate.notNull(frame);
        Validate.isTrue(arrayLocalsVar.getType().equals(Type.getType(Object[].class)));
        Validate.isTrue(tempObjectVar.getType().equals(Type.getType(Object.class)));
        validateLocalIndicies(arrayLocalsVar.getIndex(), tempObjectVar.getIndex());
        InsnList ret = new InsnList();
        
        // Load the locals
        for (int i = 0; i < frame.getLocals(); i++) {
            BasicValue basicValue = frame.getLocal(i);
            Type type = basicValue.getType();

            // If type == null, basicValue is pointing to uninitialized var -- basicValue.toString() will return ".". This means that this
            // slot contains nothing to load. So, skip this slot if we encounter it (such that it will remain uninitialized).
            if (type == null) {
                continue;
            }
            
            // If type is 'Lnull;', this means that the slot has been assigned null and that "there has been no merge yet that would 'raise'
            // the type toward some class or interface type" (from ASM mailing list). We know this slot will always contain null at this
            // point in the code so there's no specific value to load up from the array. Instead we push a null in to that slot, thereby
            // keeping the same 'Lnull;' type originally assigned to that slot (it doesn't make sense to do a CHECKCAST because 'null' is
            // not a real class and can never be a real class -- null is a reserved word in Java).
            if (type.getSort() == Type.OBJECT && "Lnull;".equals(type.getDescriptor())) {
                ret.add(new InsnNode(Opcodes.ACONST_NULL));
                ret.add(new VarInsnNode(Opcodes.ASTORE, i));
                continue;
            }
            
            // Load item from locals storage array
            ret.add(new VarInsnNode(Opcodes.ALOAD, arrayLocalsVar.getIndex()));
            ret.add(new LdcInsnNode(i));
            ret.add(new InsnNode(Opcodes.AALOAD));

            // Convert the item from an object stores it in local vars table.
            switch (type.getSort()) {
                case Type.BOOLEAN:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Boolean"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false));
                    ret.add(new VarInsnNode(Opcodes.ISTORE, i));
                    break;
                case Type.BYTE:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Byte"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false));
                    ret.add(new VarInsnNode(Opcodes.ISTORE, i));
                    break;
                case Type.SHORT:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Short"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false));
                    ret.add(new VarInsnNode(Opcodes.ISTORE, i));
                    break;
                case Type.CHAR:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Character"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false));
                    ret.add(new VarInsnNode(Opcodes.ISTORE, i));
                    break;
                case Type.INT:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Integer"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false));
                    ret.add(new VarInsnNode(Opcodes.ISTORE, i));
                    break;
                case Type.FLOAT:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Float"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false));
                    ret.add(new VarInsnNode(Opcodes.FSTORE, i));
                    break;
                case Type.LONG:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Long"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false));
                    ret.add(new VarInsnNode(Opcodes.LSTORE, i));
                    break;
                case Type.DOUBLE:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Double"));
                    ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false));
                    ret.add(new VarInsnNode(Opcodes.DSTORE, i));
                    break;
                case Type.ARRAY:
                case Type.OBJECT:
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, basicValue.getType().getInternalName()));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, i));
                    break;
                case Type.METHOD:
                case Type.VOID:
                default:
                    throw new IllegalStateException();
            }
        }
        
        return ret;
    }
    
    /**
     * Generates instructions to save the local variables table to an object array.
     *
     * @param arrayLocalsVar variable that the object array containing local variables table is stored
     * @param tempObjectVar variable to use for temporary objects
     * @param frame execution frame at the instruction where the local variables table is to be saved
     * @return instructions to save the local variables table in to an array
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if variables have the same index, or if variables have been released, or if variables are of wrong
     * type
     */
    public static InsnList saveLocalVariableTable(Variable arrayLocalsVar, Variable tempObjectVar, Frame<BasicValue> frame) {
        Validate.notNull(arrayLocalsVar);
        Validate.notNull(tempObjectVar);
        Validate.notNull(frame);
        Validate.isTrue(arrayLocalsVar.getType().equals(Type.getType(Object[].class)));
        Validate.isTrue(tempObjectVar.getType().equals(Type.getType(Object.class)));
        validateLocalIndicies(arrayLocalsVar.getIndex(), tempObjectVar.getIndex());
        InsnList ret = new InsnList();

        // Create array and save it in local vars table
        ret.add(new LdcInsnNode(frame.getLocals()));
        ret.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        ret.add(new VarInsnNode(Opcodes.ASTORE, arrayLocalsVar.getIndex()));

        // Save the locals
        for (int i = 0; i < frame.getLocals(); i++) {
            BasicValue basicValue = frame.getLocal(i);
            Type type = basicValue.getType();

            // If type == null, basicValue is pointing to uninitialized var -- basicValue.toString() will return '.'. This means that this
            // slot contains nothing to save. So, skip this slot if we encounter it.
            if (type == null) {
                continue;
            }
            
            // If type is 'Lnull;', this means that the slot has been assigned null and that "there has been no merge yet that would 'raise'
            // the type toward some class or interface type" (from ASM mailing list). We know this slot will always contain null at this
            // point in the code so we can avoid saving it. When we load it back up, we can simply push a null in to that slot, thereby
            // keeping the same 'Lnull;' type.
            if ("Lnull;".equals(type.getDescriptor())) {
                continue;
            }

            // Convert the item to an object (if not already an object) and stores it in array.
            switch (type.getSort()) {
                case Type.BOOLEAN:
                    ret.add(new VarInsnNode(Opcodes.ILOAD, i));
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;"));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectVar.getIndex()));
                    break;
                case Type.BYTE:
                    ret.add(new VarInsnNode(Opcodes.ILOAD, i));
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectVar.getIndex()));
                    break;
                case Type.SHORT:
                    ret.add(new VarInsnNode(Opcodes.ILOAD, i));
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectVar.getIndex()));
                    break;
                case Type.CHAR:
                    ret.add(new VarInsnNode(Opcodes.ILOAD, i));
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectVar.getIndex()));
                    break;
                case Type.INT:
                    ret.add(new VarInsnNode(Opcodes.ILOAD, i));
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectVar.getIndex()));
                    break;
                case Type.FLOAT:
                    ret.add(new VarInsnNode(Opcodes.FLOAD, i));
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectVar.getIndex()));
                    break;
                case Type.LONG:
                    ret.add(new VarInsnNode(Opcodes.LLOAD, i));
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectVar.getIndex()));
                    break;
                case Type.DOUBLE:
                    ret.add(new VarInsnNode(Opcodes.DLOAD, i));
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectVar.getIndex()));
                    break;
                case Type.ARRAY:
                case Type.OBJECT:
                    ret.add(new VarInsnNode(Opcodes.ALOAD, i));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectVar.getIndex()));
                    break;
                case Type.METHOD:
                case Type.VOID:
                default:
                    throw new IllegalStateException();
            }

            // Store item in to locals storage array
            ret.add(new VarInsnNode(Opcodes.ALOAD, arrayLocalsVar.getIndex()));
            ret.add(new LdcInsnNode(i));
            ret.add(new VarInsnNode(Opcodes.ALOAD, tempObjectVar.getIndex()));
            ret.add(new InsnNode(Opcodes.AASTORE));
        }

        return ret;
    }

    /**
     * Generates instructions that returns a dummy value. Return values are as follows:
     * <ul>
     * <li>void -&gt; no value</li>
     * <li>boolean -&gt; false</li>
     * <li>byte/short/char/int -&gt; 0</li>
     * <li>long -&gt; 0L</li>
     * <li>float -&gt; 0.0f</li>
     * <li>double -&gt; 0.0</li>
     * <li>Object -&gt; null</li>
     * </ul>
     *
     * @param returnType return type of the method this generated bytecode is for
     * @return instructions to return a dummy value
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code returnType}'s sort is of {@link Type#METHOD}
     */
    public static InsnList returnDummy(Type returnType) {
        Validate.notNull(returnType);
        Validate.isTrue(returnType.getSort() != Type.METHOD);

        InsnList ret = new InsnList();

        switch (returnType.getSort()) {
            case Type.VOID:
                ret.add(new InsnNode(Opcodes.RETURN));
                break;
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.SHORT:
            case Type.CHAR:
            case Type.INT:
                ret.add(new InsnNode(Opcodes.ICONST_0));
                ret.add(new InsnNode(Opcodes.IRETURN));
                break;
            case Type.LONG:
                ret.add(new InsnNode(Opcodes.LCONST_0));
                ret.add(new InsnNode(Opcodes.LRETURN));
                break;
            case Type.FLOAT:
                ret.add(new InsnNode(Opcodes.FCONST_0));
                ret.add(new InsnNode(Opcodes.FRETURN));
                break;
            case Type.DOUBLE:
                ret.add(new InsnNode(Opcodes.DCONST_0));
                ret.add(new InsnNode(Opcodes.DRETURN));
                break;
            case Type.OBJECT:
            case Type.ARRAY:
                ret.add(new InsnNode(Opcodes.ACONST_NULL));
                ret.add(new InsnNode(Opcodes.ARETURN));
                break;
            default:
                throw new IllegalStateException();
        }

        return ret;
    }

    /**
     * Generates instructions that returns a value.
     *
     * @param returnType return type of the method this generated bytecode is for
     * @param returnValueInsnList instructions that produce the return value (should leave it on the top of the stack)
     * @return instructions to return a value
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code returnType}'s sort is of {@link Type#METHOD}
     */
    public static InsnList returnValue(Type returnType, InsnList returnValueInsnList) {
        Validate.notNull(returnType);
        Validate.isTrue(returnType.getSort() != Type.METHOD);

        InsnList ret = new InsnList();
        
        ret.add(returnValueInsnList);

        switch (returnType.getSort()) {
            case Type.VOID:
                ret.add(new InsnNode(Opcodes.RETURN));
                break;
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.SHORT:
            case Type.CHAR:
            case Type.INT:
                ret.add(new InsnNode(Opcodes.IRETURN));
                break;
            case Type.LONG:
                ret.add(new InsnNode(Opcodes.LRETURN));
                break;
            case Type.FLOAT:
                ret.add(new InsnNode(Opcodes.FRETURN));
                break;
            case Type.DOUBLE:
                ret.add(new InsnNode(Opcodes.DRETURN));
                break;
            case Type.OBJECT:
            case Type.ARRAY:
                ret.add(new InsnNode(Opcodes.ARETURN));
                break;
            default:
                throw new IllegalStateException();
        }

        return ret;
    }
    
    private static void validateLocalIndicies(int ... indicies) {
        Arrays.stream(indicies).forEach((x) -> Validate.isTrue(x >= 0));
        long uniqueCount = Arrays.stream(indicies).distinct().count();
        Validate.isTrue(uniqueCount == indicies.length);
    }
}
