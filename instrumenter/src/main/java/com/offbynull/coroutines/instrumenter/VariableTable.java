package com.offbynull.coroutines.instrumenter;

import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Opcodes;

final class VariableTable {
    private final int continuationIndex;
    private final int operandStackArrayIndex;
    private final int localVarTableArrayIndex;
    private final int tempObjectIndex;
    
    public VariableTable(int access, int maxLocals) {
        int minLocals = (access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC ? 0 : 1;
        Validate.isTrue(maxLocals >= minLocals);
        
        
        // first param -- if static then 0, otherwise 1 (0th idx is this ptr for non-static methods)
        continuationIndex = minLocals;
        
        
        int counter = 0;
        counter++;
        tempObjectIndex = maxLocals + counter;
        counter++;
        operandStackArrayIndex = maxLocals + counter;
        counter++;
        localVarTableArrayIndex = maxLocals + counter;
    }

    public int getContinuationIndex() {
        return continuationIndex;
    }

    public int getOperandStackArrayIndex() {
        return operandStackArrayIndex;
    }

    public int getLocalVarTableArrayIndex() {
        return localVarTableArrayIndex;
    }

    public int getTempObjectIndex() {
        return tempObjectIndex;
    }
    
    
}
