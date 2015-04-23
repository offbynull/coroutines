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
package com.offbynull.coroutines.instrumenter;

import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.loadVar;
import static com.offbynull.coroutines.instrumenter.asm.InstructionUtils.saveVar;
import com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

final class ContinuationPointInstructionUtils {
    private ContinuationPointInstructionUtils() {
        // do nothing
    }
    
    // casts item on top of stack to object, and saves to variable
    static InsnList castToObjectAndSave(Type originalType, Variable variable) {
        Validate.notNull(originalType);
        Validate.notNull(variable);
        Validate.isTrue(variable.getType().equals(Type.getType(Object.class)));

        InsnList ret = new InsnList();
        switch (originalType.getSort()) {
            case Type.BOOLEAN:
                ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
                ret.add(saveVar(variable)); // save it in to the returnValObj
                break;
            case Type.BYTE:
                ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false));
                ret.add(saveVar(variable)); // save it in to the returnValObj
                break;
            case Type.SHORT:
                ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false));
                ret.add(saveVar(variable)); // save it in to the returnValObj
                break;
            case Type.CHAR:
                ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false));
                ret.add(saveVar(variable)); // save it in to the returnValObj
                break;
            case Type.INT:
                ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
                ret.add(saveVar(variable)); // save it in to the returnValObj
                break;
            case Type.FLOAT:
                ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false));
                ret.add(saveVar(variable)); // save it in to the returnValObj
                break;
            case Type.LONG:
                ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false));
                ret.add(saveVar(variable)); // save it in to the returnValObj
                break;
            case Type.DOUBLE:
                ret.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false));
                ret.add(saveVar(variable)); // save it in to the returnValObj
                break;
            case Type.ARRAY:
            case Type.OBJECT:
                ret.add(saveVar(variable)); // save it in to the returnValObj
                break;
            case Type.VOID:
                break;
            case Type.METHOD:
            default:
                throw new IllegalArgumentException();
        }
        
        return ret;
    }

    // casts variable to original type and puts on top of stack
    static InsnList loadAndCastToOriginal(Type originalType, Variable variable) {
        Validate.notNull(originalType);
        Validate.notNull(variable);
        Validate.isTrue(variable.getType().equals(Type.getType(Object.class)));

        InsnList ret = new InsnList();
        
        switch (originalType.getSort()) {
            case Type.BOOLEAN:
                ret.add(loadVar(variable)); // load it in to the returnValObj
                ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Boolean"));
                ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false));
                break;
            case Type.BYTE:
                ret.add(loadVar(variable)); // load it in to the returnValObj
                ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Byte"));
                ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false));
                break;
            case Type.SHORT:
                ret.add(loadVar(variable)); // load it in to the returnValObj
                ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Short"));
                ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false));
                break;
            case Type.CHAR:
                ret.add(loadVar(variable)); // load it in to the returnValObj
                ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Character"));
                ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false));
                break;
            case Type.INT:
                ret.add(loadVar(variable)); // load it in to the returnValObj
                ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Integer"));
                ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false));
                break;
            case Type.FLOAT:
                ret.add(loadVar(variable)); // load it in to the returnValObj
                ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Float"));
                ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false));
                break;
            case Type.LONG:
                ret.add(loadVar(variable)); // load it in to the returnValObj
                ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Long"));
                ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false));
                break;
            case Type.DOUBLE:
                ret.add(loadVar(variable)); // load it in to the returnValObj
                ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Double"));
                ret.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false));
                break;
            case Type.ARRAY:
            case Type.OBJECT:
                ret.add(loadVar(variable)); // load it in to the returnValObj
                ret.add(new TypeInsnNode(Opcodes.CHECKCAST, originalType.getInternalName()));
                break;
            case Type.VOID:
                break;
            case Type.METHOD:
            default:
                throw new IllegalArgumentException();
        }

        return ret;
    }

    static InsnList throwThrowableInVariable(Variable variable) {
        Validate.notNull(variable);
        Validate.isTrue(variable.getType().equals(Type.getType(Object.class)));

        InsnList ret = new InsnList();
        
        ret.add(loadVar(variable)); // load it in to the returnValObj
        ret.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Throwable"));
        ret.add(new InsnNode(Opcodes.ATHROW));
        
        return ret;
    }
}
