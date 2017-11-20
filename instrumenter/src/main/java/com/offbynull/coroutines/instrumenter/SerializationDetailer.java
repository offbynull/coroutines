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
package com.offbynull.coroutines.instrumenter;

import static com.offbynull.coroutines.instrumenter.asm.SearchUtils.findLocalVariableNodeForInstruction;
import java.util.Locale;
import org.apache.commons.collections4.list.UnmodifiableList;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.BasicValue;

final class SerializationDetailer {
    
    // MUST BE CALLED PRIOR TO INSTRUMENTATION!!!!
    public void detail(MethodNode methodNode, MethodAttributes attrs, StringBuilder output) {
        Validate.notNull(methodNode);
        Validate.notNull(attrs);
        Validate.notNull(output);

        int methodId = attrs.getSignature().getMethodId();

        output.append("Class Name: ").append(attrs.getSignature().getClassName().replace('/', '.')).append('\n');
        output.append("Method Name: ").append(attrs.getSignature().getMethodName()).append('\n');
        output.append("Method Params: ").append(attrs.getSignature().getMethodDescriptor()).append('\n');
        output.append("Method Return: ").append(attrs.getSignature().getReturnType()).append('\n');
        output.append("Method ID: ").append(methodId).append('\n');
        output.append("------------------------------------\n");

        UnmodifiableList<ContinuationPoint> cps = attrs.getContinuationPoints();
        for (int i = 0; i < cps.size(); i++) {
            ContinuationPoint cp = cps.get(i);

            int line = cp.getLineNumber() == null ? -1 : cp.getLineNumber();
            String header = String.format("Continuation Point ID: %-4d Line: %-4d Type: %s",
                    i,
                    line,
                    cp.getClass().getSimpleName());
            output.append(header).append('\n');

            // Check out PackStateGenerators class for how things are organized. Brief overview follows...
            // container[0] has local variables that are bytes/shorts/ints
            // container[1] has local variables that are floats
            // container[2] has local variables that are longs
            // container[3] has local variables that are doubles
            // container[4] has local variables that are Objects
            // container[5] has operands that are bytes/shorts/ints
            // container[6] has operands that are floats
            // container[7] has operands that are longs
            // container[8] has operands that are doubles
            // container[9] has operands that are Objects
            detailLocals(cp, methodNode, output);
            detailOperands(cp, output);

            output.append('\n');
        }
        
        output.append('\n');
    }

    private void detailLocals(ContinuationPoint cp, MethodNode methodNode, StringBuilder output) {
        int intIdx = 0;
        int floatIdx = 0;
        int doubleIdx = 0;
        int longIdx = 0;
        int objectIdx = 0;

        for (int j = 0; j < cp.getFrame().getLocals(); j++) {
            BasicValue local = cp.getFrame().getLocal(j);

            if (local.getType() == null) {
                // unused in frame, so skip over it
                continue;
            }

            LocalVariableNode lvn = findLocalVariableNodeForInstruction(
                    methodNode.localVariables,
                    methodNode.instructions,
                    cp.getInvokeInstruction(),
                    j);

            String name;
            if (lvn == null || lvn.name == null) {
                name = "???????";
            } else {
                name = lvn.name;
            }

            String accessor;
            String type = null;
            switch (local.getType().getSort()) {
                case Type.INT:
                    accessor = "varInts[" + intIdx + "]";
                    type = "int";
                    intIdx++;
                    break;
                case Type.FLOAT:
                    accessor = "varFloats[" + floatIdx + "]";
                    type = "float";
                    floatIdx++;
                    break;
                case Type.LONG:
                    accessor = "varLongs[" + longIdx + "]";
                    type = "long";
                    longIdx++;
                    break;
                case Type.DOUBLE:
                    accessor = "varDoubles[" + doubleIdx + "]";
                    type = "double";
                    doubleIdx++;
                    break;
                case Type.OBJECT:
                    accessor = "varObjects[" + objectIdx + "]";
                    type = local.getType().toString();
                    objectIdx++;
                    break;
                default:
                    throw new IllegalStateException(local.getType().toString()); // should never happen
            }

            String line = String.format(Locale.ENGLISH, "  %-20s // LVT index is %d / name is %s / type is %s",
                    accessor,
                    j,
                    name,
                    type);
            output.append(line).append('\n');
        }
    }

    private void detailOperands(ContinuationPoint cp, StringBuilder output) {
        int intIdx = 0;
        int floatIdx = 0;
        int doubleIdx = 0;
        int longIdx = 0;
        int objectIdx = 0;

        for (int j = 0; j < cp.getFrame().getStackSize(); j++) {
            BasicValue operand = cp.getFrame().getStack(j);

            String accessor;
            String type = "";
            switch (operand.getType().getSort()) {
                case Type.INT:
                    accessor = "operandInts[" + intIdx + "]";
                    type = "int";
                    intIdx++;
                    break;
                case Type.FLOAT:
                    accessor = "operandFloats[" + floatIdx + "]";
                    type = "float";
                    floatIdx++;
                    break;
                case Type.LONG:
                    accessor = "operandLongs[" + longIdx + "]";
                    type = "long";
                    longIdx++;
                    break;
                case Type.DOUBLE:
                    accessor = "operandDoubles[" + doubleIdx + "]";
                    type = "double";
                    doubleIdx++;
                    break;
                case Type.OBJECT:
                    accessor = "operandObjects[" + objectIdx + "]";
                    type = operand.getType().toString();
                    objectIdx++;
                    break;
                default:
                    throw new IllegalStateException(operand.getType().toString()); // should never happen
            }

            String line = String.format(Locale.ENGLISH, "  %-20s // operand index is %d / type is %s",
                    accessor,
                    j,
                    type);
            output.append(line).append('\n');
        }
    }
}
