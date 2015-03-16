package com.offbynull.coroutines.user;

import java.util.LinkedList;

public final class Continuation {
    public static final int MODE_NORMAL = 0;
    public static final int MODE_SAVING = 1;
    public static final int MODE_LOADING = 2;
    private LinkedList savedMethodStates = new LinkedList();
    private LinkedList pendingMethodStates = new LinkedList();
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
    
    public void insertPending(MethodState methodState) {
        if (methodState == null) {
            throw new NullPointerException();
        }
        pendingMethodStates.addLast(methodState);
    }

    public void removePending() {
        if (pendingMethodStates.isEmpty()) {
            throw new IllegalStateException();
        }
        pendingMethodStates.removeLast();
    }
    
    public MethodState removeSaved() {
        if (savedMethodStates.isEmpty()) {
            throw new IllegalStateException();
        }
        return (MethodState) savedMethodStates.removeFirst();
    }
    
    public void reset() {
        pendingMethodStates.clear();
        savedMethodStates.clear();
        mode = MODE_NORMAL;
    }

    public void replaceSavedMethodStates() {
        System.out.println("switch");
        savedMethodStates = pendingMethodStates;
        pendingMethodStates = new LinkedList();
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
