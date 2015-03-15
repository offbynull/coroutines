package com.offbynull.coroutines.user;

import java.util.Deque;
import java.util.LinkedList;
import org.apache.commons.lang3.Validate;

public final class Continuation {
    public static final int MODE_NORMAL = 0;
    public static final int MODE_SAVING = 1;
    public static final int MODE_LOADING = 2;
    private Deque<MethodState> methodStates = new LinkedList<>();
    private int mode = MODE_NORMAL;

    Continuation() {
        // do nothing
    }
    
    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        Validate.isTrue(mode == MODE_NORMAL || mode == MODE_SAVING || mode == MODE_LOADING);
        this.mode = mode;
    }
    
    public void insertLast(MethodState methodState) {
        Validate.notNull(methodState);
        methodStates.addLast(methodState);
    }
    
    public MethodState removeFirst() {
        Validate.validState(!methodStates.isEmpty());
        return methodStates.removeFirst();
    }
    
    public boolean isEmpty() {
        return methodStates.isEmpty();
    }

    public void suspend() {
        throw new UnsupportedOperationException("Caller not instrumented");
    }
    
    public static final class MethodState {
        private final int continuationPoint;
        private final Object[] stack;
        private final Object[] localTable;

        public MethodState(int continuationPoint, Object[] stack, Object[] localTable) {
            Validate.isTrue(continuationPoint >= 0);
            Validate.notNull(stack);
            Validate.notNull(localTable);
            this.continuationPoint = continuationPoint;
            this.stack = stack.clone();
            this.localTable = localTable.clone();
        }

        public int getContinuationPoint() {
            return continuationPoint;
        }

        public Object[] getStack() {
            return stack.clone();
        }

        public Object[] getLocalTable() {
            return localTable.clone();
        }
        
    }
}
