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

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Tracks extra variables used for instrumentation as well as arguments passed in to a method.
 * @author Kasra Faghihi
 */
public final class VariableTable {
    private List<Variable> argVars;
    private int extraOffset;
    private List<Variable> extraVars;
    
    /**
     * Constructs a {@link VariableTable} object.
     * @param classNode class that {@code methodeNode} resides in
     * @param methodNode method this variable table is for
     */
    public VariableTable(ClassNode classNode, MethodNode methodNode) {
        this((methodNode.access & Opcodes.ACC_STATIC) != 0, Type.getObjectType(classNode.name), Type.getType(methodNode.desc),
                methodNode.maxLocals);
        Validate.isTrue(classNode.methods.contains(methodNode)); // sanity check
    }
    
    private VariableTable(boolean isStatic, Type objectType, Type methodType, int maxLocals) {
        Validate.notNull(objectType);
        Validate.notNull(methodType);
        Validate.isTrue(maxLocals >= 0);
        Validate.isTrue(objectType.getSort() == Type.OBJECT);
        Validate.isTrue(methodType.getSort() == Type.METHOD);
        
        extraOffset = maxLocals;
        
        argVars = new ArrayList<>();
        extraVars = new ArrayList<>();
        
        if (!isStatic) {
            argVars.add(0, new Variable(objectType, 0, true));
        }
        
        Type[] argTypes = methodType.getArgumentTypes();
        for (int i = 0; i < argTypes.length; i++) {
            int idx = isStatic ? i : i + 1;
            argVars.add(new Variable(argTypes[i], idx, true));
        }
    }

    /**
     * Get the variable for an argument passed in to the method. Remember that if the method is static, arguments start at 0th index in the
     * local variables table. If it isn't static, they start at 1 (0th would be "this").
     * <p>
     * Values returned by this method must never be passed in to
     * {@link #releaseExtra(com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable) }.
     * @param index index of argument
     * @return {@link Variable} object that represents the argument
     * @throws IllegalArgumentException if {@code index} is not the index of an argument
     */
    public Variable getArgument(int index) {
        Validate.isTrue(index >= 0 && index < argVars.size());
        return argVars.get(index);
    }

    /**
     * Equivalent to calling {@code acquireExtra(Type.getType(type))}.
     * @param type type which variable is for
     * @return new variable
     * @throws NullPointerException if any argument is {@code null}
     */
    public Variable acquireExtra(Class<?> type) {
        Validate.notNull(type);
        
        return acquireExtra(Type.getType(type));
    }
    
    /**
     * Acquire a new variable for use when instrumenting some code within the method this {@link VariableTable} is for.
     * @param type type which variable is for
     * @return new variable
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code type} is of sort of {@link Type#VOID} or {@link Type#METHOD}
     */
    public Variable acquireExtra(Type type) {
        Validate.notNull(type);
        Validate.isTrue(type.getSort() != Type.VOID);
        Validate.isTrue(type.getSort() != Type.METHOD);
        
        for (Variable var : extraVars) {
            if (!var.used && var.type.equals(type)) {
                // We don't want to return the same object because other objects that may still have the existing Variable will now have
                // them marked as being usable again. Do not want that to be the case. So instead create a new object and return that.
                extraVars.remove(var);
                var = new Variable(type, var.index, true);
                extraVars.add(var);
                return var;
            }
        }
        
        Variable var = new Variable(type, extraOffset + extraVars.size(), true);
        extraVars.add(var);
        return var;
    }

    /**
     * Release a variable that was acquired with {@link #acquireExtra(org.objectweb.asm.Type) }.
     * @param variable variable to release
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code variable} wasn't acquired from this object, or if {@code variable} refers to an argument,
     * or if {@code variable} has already been released
     */
    public void releaseExtra(Variable variable) {
        Validate.notNull(variable);
        Validate.isTrue(variable.getParent() == this);
        Validate.isTrue(variable.index >= argVars.size());
        Validate.isTrue(variable.used);

        variable.used = false;
    }
    
    /**
     * Represents an entry within the local variable table of a method.
     */
    public final class Variable {
        private Type type;
        private int index;
        private boolean used;

        private Variable(Type type, int index, boolean used) {
            this.type = type;
            this.index = index;
            this.used = used;
        }

        /**
         * Get the type of this local variable table entry.
         * @return type of this entry
         * @throws IllegalArgumentException if this {@link Variable} has been released
         */
        public Type getType() {
            Validate.isTrue(used);
            return type;
        }

        /**
         * Get the index of this entry within the local variable table.
         * @return index of this entry
         * @throws IllegalArgumentException if this {@link Variable} has been released
         */
        public int getIndex() {
            Validate.isTrue(used);
            return index;
        }

        /**
         * Returns {@code true} if this object hasn't been released.
         * @see VariableTable#releaseExtra(com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable) 
    
         * @return index of this entry
         * @throws IllegalArgumentException if this {@link Variable} has been released
         */
        public boolean isUsed() {
            return used;
        }
        
        private VariableTable getParent() {
            return VariableTable.this;
        }
    }
    
}
