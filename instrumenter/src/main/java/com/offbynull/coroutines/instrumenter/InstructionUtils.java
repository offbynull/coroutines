package com.offbynull.coroutines.instrumenter;

import com.offbynull.coroutines.user.InternalContinuationException;
import java.util.List;
import java.util.function.Supplier;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

public final class InstructionUtils {

    public static InsnList instrumentMethodStart(boolean staticMethod, InsnList originalMethodInsnList,
            List<AbstractInsnNode> invokePoints, List<AbstractInsnNode> yieldPoints) {
        int coroutineStateVarIdx = staticMethod ? 0 : 1;

        Type exceptionType = Type.getType(InternalContinuationException.class);

        InsnList ret = new InsnList();

        LabelNode startOfMethodLabelNode = new LabelNode();

        ret.add(
                tableSwitch(
                        throwException(exceptionType, "Bad state"),
                        0,
                        merge(
                                saveLocalsArray(0, 1, null),
                                saveOperandStack(0, 1, null)),
                        throwException(exceptionType, "Unexpected saving state"),
                        jumpTo(startOfMethodLabelNode)));
        ret.add(startOfMethodLabelNode);
        ret.add(originalMethodInsnList);

        return ret;
    }

    /**
     * Merges multiple instruction lists in to a single instruction list
     * @param insnLists instruction lists to merge
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     * @return merged instructions
     */
    private static InsnList merge(InsnList... insnLists) {
        Validate.notNull(insnLists);
        Validate.noNullElements(insnLists);

        InsnList ret = new InsnList();
        for (int i = 0; i < insnLists.length; i++) {
            ret.add(insnLists[i]);
        }

        return ret;
    }

    /**
     * Generates instructions for an unconditional jump to a label.
     * @param labelNode label to jump to
     * @throws NullPointerException if any argument is {@code null}
     * @return instructions for an unconditional jump to {@code labelNode}
     */
    private static InsnList jumpTo(LabelNode labelNode) {
        Validate.notNull(labelNode);

        InsnList ret = new InsnList();
        ret.add(new JumpInsnNode(Opcodes.GOTO, labelNode));

        return ret;
    }

    /**
     * Generates instructions to throw an exception.
     *
     * @param exceptionType type of exception (must have a constructor that takes in a single string as the message)
     * @param message message of exception
     * @return instructions to throw an exception
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if type isn't an {@code OBJECT}
     */
    private static InsnList throwException(Type exceptionType, String message) {
        Validate.notNull(exceptionType);
        Validate.notNull(message);
        Validate.isTrue(exceptionType.getSort() == Type.OBJECT);

        InsnList ret = new InsnList();

        ret.add(new TypeInsnNode(Opcodes.NEW, exceptionType.getInternalName()));
        ret.add(new InsnNode(Opcodes.DUP));
        ret.add(new LdcInsnNode("test"));
        ret.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, exceptionType.getInternalName(), "<init>", "(Ljava/lang/String;)V", false));
        ret.add(new InsnNode(Opcodes.ATHROW));

        return ret;
    }

    /**
     * Generates instructions for a switch table.
     *
     * @param defaultInsnList instructions to execute on default statement ({@code null} means no default statement)
     * @param caseStartIdx the number which the case statements start at
     * @param caseInsnLists instructions to execute on each case statement (elements that contain {@code null} mean missing case statement)
     * @return instructions to dump the operand stack in to an array and save it to the local variables table
     * @throws NullPointerException if {@code caseInsnLists} argument is {@code null}
     * @throws IllegalArgumentException if any numeric argument is {@code < 0}
     */
    private static InsnList tableSwitch(InsnList defaultInsnList, int caseStartIdx, InsnList... caseInsnLists) {
        Validate.isTrue(caseStartIdx >= 0);
        Validate.notNull(caseInsnLists);
        InsnList ret = new InsnList();

        LabelNode endLabelNode = new LabelNode();
        LabelNode defaultLabelNode = defaultInsnList == null ? endLabelNode : new LabelNode();
        LabelNode[] caseLabelNodes = new LabelNode[caseInsnLists.length];

        for (int i = 0; i < caseInsnLists.length; i++) {
            caseLabelNodes[i] = caseInsnLists[i] == null ? endLabelNode : new LabelNode();
        }

        ret.add(new TableSwitchInsnNode(caseStartIdx, caseStartIdx + caseInsnLists.length, defaultLabelNode, caseLabelNodes));

        for (int i = 0; i < caseInsnLists.length; i++) {
            if (caseInsnLists[i] != null) {
                ret.add(caseInsnLists[i]);
                ret.add(new JumpInsnNode(Opcodes.GOTO, endLabelNode));
            }
        }

        if (defaultInsnList != null) {
            ret.add(defaultInsnList);
            ret.add(new JumpInsnNode(Opcodes.GOTO, endLabelNode));
        }

        return ret;
    }

    /**
     * Generates instructions to save the operand stack to an object array.
     *
     * @param arrayLocalsIdx index within the local variables table that the generated object array should be saved
     * @param tempObjectLocalsIdx index within the local variables table that a temporary object should be stored
     * @param frame execution stack frame at the instruction where the operand stack is to be saved
     * @return instructions to dump the operand stack in to an array and save it to the local variables table
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if any numeric argument is {@code < 0}
     */
    private static InsnList saveOperandStack(int arrayLocalsIdx, int tempObjectLocalsIdx,
            Frame<BasicValue> frame) {
        Validate.isTrue(arrayLocalsIdx >= 0);
        Validate.isTrue(tempObjectLocalsIdx >= 0);
        Validate.notNull(frame);
        InsnList ret = new InsnList();

        // Create stack storage array and save it in local vars table
        ret.add(new LdcInsnNode(frame.getStackSize()));
        ret.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        ret.add(new VarInsnNode(Opcodes.ASTORE, arrayLocalsIdx));

        // Save the stack
        for (int i = frame.getStackSize() - 1; i >= 0; i--) {
            BasicValue basicValue = frame.getStack(i);
            Type type = basicValue.getType();

            // Convert the item to an object (if not already an object) and stores it in local vars table. Item removed from stack.
            switch (type.getSort()) {
                case Type.BOOLEAN:
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;"));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectLocalsIdx));
                    break;
                case Type.BYTE:
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectLocalsIdx));
                    break;
                case Type.SHORT:
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectLocalsIdx));
                    break;
                case Type.CHAR:
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectLocalsIdx));
                    break;
                case Type.INT:
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectLocalsIdx));
                    break;
                case Type.FLOAT:
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectLocalsIdx));
                    break;
                case Type.LONG:
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectLocalsIdx));
                    break;
                case Type.DOUBLE:
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectLocalsIdx));
                    break;
                case Type.ARRAY:
                case Type.OBJECT:
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectLocalsIdx));
                    break;
                case Type.METHOD:
                case Type.VOID:
                default:
                    throw new IllegalArgumentException();
            }

            // Store item in to stack storage array
            ret.add(new VarInsnNode(Opcodes.ALOAD, arrayLocalsIdx));
            ret.add(new LdcInsnNode(i));
            ret.add(new VarInsnNode(Opcodes.ALOAD, tempObjectLocalsIdx));
            ret.add(new InsnNode(Opcodes.AASTORE));
        }

        // Restore the stack
        for (int i = 0; i < frame.getStackSize(); i++) {
            BasicValue basicValue = frame.getStack(i);
            Type type = basicValue.getType();

            // Load item from stack storage array
            ret.add(new VarInsnNode(Opcodes.ALOAD, arrayLocalsIdx));
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
     * Generates instructions to save the local variables table to an object array.
     *
     * @param arrayLocalsIdx index within the local variables table that the generated object array should be frame
     * @param tempObjectLocalsIdx index within the local variables table that a temporary object should be stored
     * @param frameAtInstruction execution stack frame at the instruction where the local variables table is to be saved
     * @return instructions to dump the local variables table in to an array and save it to the local variables table
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if any numeric argument is {@code < 0}
     */
    private static InsnList saveLocalsArray(int arrayLocalsIdx, int tempObjectLocalsIdx,
            Frame<BasicValue> frame) {
        Validate.isTrue(arrayLocalsIdx >= 0);
        Validate.isTrue(tempObjectLocalsIdx >= 0);
        Validate.notNull(frame);
        InsnList ret = new InsnList();

        // Create local storage array and save it in local vars table
        ret.add(new LdcInsnNode(frame.getLocals()));
        ret.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        ret.add(new VarInsnNode(Opcodes.ASTORE, arrayLocalsIdx));

        // Save the locals
        for (int i = 0; i < frame.getLocals(); i++) {
            BasicValue basicValue = frame.getLocal(i);
            Type type = basicValue.getType();

            if (type == null) {
                continue;
            }

            // Convert the item to an object (if not already an object) and stores it in local vars table.
            switch (type.getSort()) {
                case Type.BOOLEAN:
                    ret.add(new VarInsnNode(Opcodes.ILOAD, i));
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;"));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectLocalsIdx));
                    break;
                case Type.BYTE:
                    ret.add(new VarInsnNode(Opcodes.ILOAD, i));
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectLocalsIdx));
                    break;
                case Type.SHORT:
                    ret.add(new VarInsnNode(Opcodes.ILOAD, i));
                    ret.add(new InsnNode(Opcodes.DUP));
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectLocalsIdx));
                    break;
                case Type.CHAR:
                    ret.add(new VarInsnNode(Opcodes.ILOAD, i));
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectLocalsIdx));
                    break;
                case Type.INT:
                    ret.add(new VarInsnNode(Opcodes.ILOAD, i));
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectLocalsIdx));
                    break;
                case Type.FLOAT:
                    ret.add(new VarInsnNode(Opcodes.FLOAD, i));
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectLocalsIdx));
                    break;
                case Type.LONG:
                    ret.add(new VarInsnNode(Opcodes.LLOAD, i));
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectLocalsIdx));
                    break;
                case Type.DOUBLE:
                    ret.add(new VarInsnNode(Opcodes.DLOAD, i));
                    ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectLocalsIdx));
                    break;
                case Type.ARRAY:
                case Type.OBJECT:
                    ret.add(new VarInsnNode(Opcodes.ALOAD, i));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, tempObjectLocalsIdx));
                    break;
                case Type.METHOD:
                case Type.VOID:
                default:
                    throw new IllegalStateException();
            }

            // Store item in to locals storage array
            ret.add(new VarInsnNode(Opcodes.ALOAD, arrayLocalsIdx));
            ret.add(new LdcInsnNode(i));
            ret.add(new VarInsnNode(Opcodes.ALOAD, tempObjectLocalsIdx));
            ret.add(new InsnNode(Opcodes.AASTORE));
        }

        // Shove in to method nodes
        return ret;
    }

    /**
     * Generates instructions that returns a dummy value. Return values are as follows:
     * <ul>
     * <li>void -> no value</li>
     * <li>boolean -> false</li>
     * <li>byte/short/char/int -> 0</li>
     * <li>long -> 0L</li>
     * <li>float -> 0.0f</li>
     * <li>double -> 0.0</li>
     * <li>Object -> null</li>
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
}
