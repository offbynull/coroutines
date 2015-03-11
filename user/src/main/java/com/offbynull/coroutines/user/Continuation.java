package com.offbynull.coroutines.user;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.Validate;

public final class Continuation {
    private static final int MODE_NORMAL = 0;
    private static final int MODE_SAVING = 1;
    private static final int MODE_LOADING = 2;
    private Deque<MethodState> methodStates = new LinkedList<>();
    private int mode = MODE_NORMAL;

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        Validate.isTrue(mode == MODE_NORMAL || mode == MODE_SAVING || mode == MODE_LOADING);
        this.mode = mode;
    }
    
    public void push(MethodState methodState) {
        Validate.notNull(methodState);
        methodStates.push(methodState);
    }
    
    public MethodState pop() {
        Validate.validState(!methodStates.isEmpty());
        return methodStates.pop();
    }
    
    public void suspend() {
        throw new UnsupportedOperationException("Caller not instrumented");
    }
    
    public static final class MethodState {
        private final int continuationPoint;
        private final List<Object> stack;
        private final List<Object> localTable;

        public MethodState(int continuationPoint, Object[] stack, Object[] localTable) {
            Validate.isTrue(continuationPoint >= 0);
            Validate.notNull(stack);
            Validate.notNull(localTable);
            this.continuationPoint = continuationPoint;
            this.stack = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(stack)));
            this.localTable = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(localTable)));
        }

        public int getContinuationPoint() {
            return continuationPoint;
        }

        public List<Object> getStack() {
            return stack;
        }

        public List<Object> getLocalTable() {
            return localTable;
        }
        
    }
}
