package com.offbynull.coroutines.user;

import java.util.Deque;
import java.util.LinkedList;

public final class Continuation {
    public static final int MODE_NORMAL = 0;
    public static final int MODE_SAVING = 1;
    public static final int MODE_LOADING = 2;
    private Deque methodStates = new LinkedList();
    private int mode = MODE_NORMAL;

    Continuation() {
        // do nothing
    }
    
    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        if (!(mode == MODE_NORMAL || mode == MODE_SAVING || mode == MODE_LOADING)) {
            throw new IllegalArgumentException();
        }
        this.mode = mode;
    }
    
    public void insertLast(MethodState methodState) {
        if (methodState == null) {
            throw new NullPointerException();
        }
        methodStates.addLast(methodState);
    }
    
    public MethodState removeFirst() {
        if (methodStates.isEmpty()) {
            throw new IllegalStateException();
        }
        return (MethodState) methodStates.removeFirst();
    }
    
    public boolean isEmpty() {
        return methodStates.isEmpty();
    }
    
    public void reset() {
        methodStates.clear();
        mode = MODE_NORMAL;
    }

    public void suspend() {
        throw new UnsupportedOperationException("Caller not instrumented");
    }
    
    public static final class MethodState {
        private final int continuationPoint;
        private final Object[] stack;
        private final Object[] localTable;

        public MethodState(int continuationPoint, Object[] stack, Object[] localTable) {
            if (continuationPoint < 0) {
                throw new IllegalArgumentException();
            }
            if (stack == null || localTable == null) {
                throw new NullPointerException();
            }
            this.continuationPoint = continuationPoint;
            this.stack = (Object[]) stack.clone();
            this.localTable = (Object[]) localTable.clone();
        }

        public int getContinuationPoint() {
            return continuationPoint;
        }

        public Object[] getStack() {
            return (Object[]) stack.clone();
        }

        public Object[] getLocalTable() {
            return (Object[]) localTable.clone();
        }
    }
}
