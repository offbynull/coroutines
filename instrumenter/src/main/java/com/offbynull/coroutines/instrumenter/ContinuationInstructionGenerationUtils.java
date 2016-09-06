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
package com.offbynull.coroutines.instrumenter;

import static com.offbynull.coroutines.instrumenter.asm.MethodInvokeUtils.getReturnTypeOfInvocation;
import com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable;
import java.util.Arrays;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

/**
 * Utility class to generate bytecode instructions specific to Coroutines/Continuations.
 * @author Kasra Faghihi
 */
final class ContinuationInstructionGenerationUtils {
    private ContinuationInstructionGenerationUtils() {
        // do nothing
    }
    
    /**
     * Generates instructions to load the operand stack from an object array.
     * @param arrayStackVar variable that the object array containing operand stack is stored
     * @param frame execution frame at the instruction for which the operand stack is to be restored
     * @return instructions to load the operand stack from an array
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if variables have the same index, or if variables have been released, or if variables are of wrong
     * type
     */
    public static InsnList loadOperandStack(Variable arrayStackVar, Frame<BasicValue> frame) {
        return loadOperandStack(arrayStackVar, frame, 0, frame.getStackSize());
    }

    /**
     * Generates instructions to load the last {@code count} items of the operand stack from an object array. The object array contains all
     * items for the stack, but only the tail {@code count} items will be loaded on to the stack.
     * @param arrayStackVar variable that the object array containing operand stack is stored
     * @param frame execution frame at the instruction for which the operand stack is to be restored
     * @param count number of items to load to the bottom of the stack.
     * @return instructions to load the relevant portion of the operand stack from an array
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if variables have the same index, or if variables have been released, or if variables are of wrong
     * type, or if there aren't {@code count} items on the stack
     */
    public static InsnList loadOperandStackSuffix(Variable arrayStackVar, Frame<BasicValue> frame, int count) {
        int start = frame.getStackSize() - count;
        int end = frame.getStackSize();
        Validate.isTrue(start >= 0);
        return loadOperandStack(arrayStackVar, frame, start, end);
    }

    /**
     * Generates instructions to load the first {@code count} items of the operand stack from an object array. The object array contains all
     * items for the stack, but only the beginning {@code count} items will be loaded on to the stack.
     * @param arrayStackVar variable that the object array containing operand stack is stored
     * @param frame execution frame at the instruction for which the operand stack is to be restored
     * @param count number of items to load to the bottom of the stack.
     * @return instructions to load the relevant portion of operand stack from an array
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if variables have the same index, or if variables have been released, or if variables are of wrong
     * type, or if there aren't {@code count} items on the stack
     */
    public static InsnList loadOperandStackPrefix(Variable arrayStackVar, Frame<BasicValue> frame, int count) {
        int start = 0;
        int end = count;
        Validate.isTrue(end <= frame.getStackSize());
        return loadOperandStack(arrayStackVar, frame, start, end);
    }
    
    private static InsnList loadOperandStack(Variable arrayStackVar, Frame<BasicValue> frame, int start, int end) {
        Validate.notNull(arrayStackVar);
        Validate.notNull(frame);
        Validate.isTrue(arrayStackVar.getType().equals(Type.getType(Object[].class)));
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
        return saveOperandStack(arrayStackVar, tempObjectVar, frame, frame.getStackSize(), frame.getStackSize());
    }

    /**
     * Generates instructions to save a certain number of items from the top of the operand stack to an object array.
     * @param arrayStackVar variable that the object array containing operand stack is stored
     * @param tempObjectVar variable to use for temporary objects
     * @param frame execution frame at the instruction where the operand stack is to be saved
     * @param top treat the stack returned by {@code frame} as if it has a size of this value starting from the bottom (this is useful for
     * when you've already saved some of the stack and now you want to save the remainder)
     * @param count number of items to store from the stack
     * @return instructions to save the operand stack in to an array and save it to the local variables table
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if variables have the same index, or if variables have been released, or if variables are of wrong
     * type, or if {@code top} is larger than the number of items in the stack at {@code frame} (or is negative), or if {@code count} is
     * larger than {@code top} (or is negative)
     */
    public static InsnList saveOperandStack(Variable arrayStackVar, Variable tempObjectVar, Frame<BasicValue> frame, int top, int count) {
        Validate.notNull(arrayStackVar);
        Validate.notNull(tempObjectVar);
        Validate.notNull(frame);
        Validate.isTrue(arrayStackVar.getType().equals(Type.getType(Object[].class)));
        Validate.isTrue(tempObjectVar.getType().equals(Type.getType(Object.class)));
        validateLocalIndicies(arrayStackVar.getIndex(), tempObjectVar.getIndex());
        Validate.isTrue(top <= frame.getStackSize());
        Validate.isTrue(count <= top);
        Validate.isTrue(top >= 0);
        Validate.isTrue(count >= 0);
        
        InsnList ret = new InsnList();

        // Create stack storage array and save it in local vars table
        ret.add(new LdcInsnNode(count));
        ret.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        ret.add(new VarInsnNode(Opcodes.ASTORE, arrayStackVar.getIndex()));

        // Save the stack
        int beforeSavedStackPos = top - 1;
        int afterSavedStackPos = top - count;
        for (int i = beforeSavedStackPos; i >= afterSavedStackPos; i--) {
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
            int stackPos = beforeSavedStackPos - i;
            int saveToArrayIdx = count - stackPos - 1;
            ret.add(new VarInsnNode(Opcodes.ALOAD, arrayStackVar.getIndex()));
            ret.add(new LdcInsnNode(saveToArrayIdx));
            ret.add(new VarInsnNode(Opcodes.ALOAD, tempObjectVar.getIndex()));
            ret.add(new InsnNode(Opcodes.AASTORE));
        }

        // At this point, the object array containing the saved stack will be ordered such that the last element in the array will have the
        // top of the stack.
        //
        // For example...
        //
        //                              top of stack
        //                                   |
        //   stack = [item1, item2, item3, item4]
        //   array = [item1, item2, item3, item4]
        //              |                    |
        //             idx0                 idx3
        
        // Restore the stack
        for (int i = afterSavedStackPos; i <= beforeSavedStackPos; i++) {
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
            int loadFromArrayIdx = i - afterSavedStackPos;
            ret.add(new VarInsnNode(Opcodes.ALOAD, arrayStackVar.getIndex()));
            ret.add(new LdcInsnNode(loadFromArrayIdx));
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
     * @param frame execution frame at the instruction for which the local variables table is to be restored
     * @return instructions to load the local variables table from an array
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if variables have the same index, or if variables have been released, or if variables are of wrong
     * type
     */
    public static InsnList loadLocalVariableTable(Variable arrayLocalsVar, Frame<BasicValue> frame) {
        Validate.notNull(arrayLocalsVar);
        Validate.notNull(frame);
        Validate.isTrue(arrayLocalsVar.getType().equals(Type.getType(Object[].class)));
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
     * Generates instructions to pop the result of the method off the stack. This will only generate instructions if the method being
     * invoked generates a return value.
     * @param invokeInsnNode instruction for the method that was invoked (can either be of type {@link MethodInsnNode} or
     * {@link InvokeDynamicInsnNode} -- this is used to determine how many items to pop off the stack
     * @return instructions for a pop (only if the method being invoked generates a return value)
     * @throws IllegalArgumentException if {@code invokeInsnNode} isn't of type {@link MethodInsnNode} or {@link InvokeDynamicInsnNode}
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList popMethodResult(AbstractInsnNode invokeInsnNode) {
        Validate.notNull(invokeInsnNode);
        
        Type returnType = getReturnTypeOfInvocation(invokeInsnNode);
        
        InsnList ret = new InsnList();
        switch (returnType.getSort()) {
            case Type.LONG:
            case Type.DOUBLE:
                ret.add(new InsnNode(Opcodes.POP2));
                break;
            case Type.VOID:
                break;
            case Type.METHOD:
                throw new IllegalStateException(); // this should never happen
            default:
                ret.add(new InsnNode(Opcodes.POP));
                break;
        }

        return ret;
    }
    
    private static void validateLocalIndicies(int ... indicies) {
        Arrays.stream(indicies).forEach((x) -> Validate.isTrue(x >= 0));
        long uniqueCount = Arrays.stream(indicies).distinct().count();
        Validate.isTrue(uniqueCount == indicies.length);
    }
}
