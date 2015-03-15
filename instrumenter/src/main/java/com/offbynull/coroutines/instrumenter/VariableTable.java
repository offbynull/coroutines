package com.offbynull.coroutines.instrumenter;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public final class VariableTable {
    private List<Variable> argVars;
    private int extraOffset;
    private List<Variable> extraVars;
    
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

    public Variable getArgument(int index) {
        Validate.isTrue(index >= 0 && index < argVars.size());
        return argVars.get(index);
    }
    
    public Variable acquireExtra(Type type) {
        Validate.notNull(type);
        Validate.isTrue(type.getSort() != Type.VOID);
        Validate.isTrue(type.getSort() != Type.METHOD);
        
        for (Variable var : extraVars) {
            if (!var.used && var.type.equals(type)) {
                var.used = true;
                return var;
            }
        }
        
        Variable var = new Variable(type, extraOffset + extraVars.size(), true);
        extraVars.add(var);
        return var;
    }

    public void releaseExtra(Variable variable) {
        Validate.notNull(variable);
        Validate.isTrue(variable.index >= 0);
        Validate.isTrue(variable.used);

        variable.used = false;
    }
    
    public final class Variable {
        private Type type;
        private int index;
        private boolean used;

        private Variable(Type type, int index, boolean used) {
            this.type = type;
            this.index = index;
            this.used = used;
        }

        public Type getType() {
            return type;
        }

        public int getIndex() {
            return index;
        }

        public boolean isUsed() {
            return used;
        }
        
    }
    
}
