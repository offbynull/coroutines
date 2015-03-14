package com.offbynull.coroutines.instrumenter;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Type;

public final class VariableTable {
    private List<Variable> argVars;
    private int extraOffset;
    private List<Variable> extraVars;
    
    public VariableTable(boolean isStatic, Type methodType, int maxLocals) {
        Validate.notNull(methodType);
        Validate.isTrue(maxLocals >= 0);
        
        extraOffset = maxLocals;
        
        argVars = new ArrayList<>();
        extraVars = new ArrayList<>();
        
        if (!isStatic) {
            argVars.add(0, new Variable(Type.getType(Object.class), 0, true));
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
