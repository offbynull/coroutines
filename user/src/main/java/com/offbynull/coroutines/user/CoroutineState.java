package com.offbynull.coroutines.user;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.Validate;

public final class CoroutineState {
    private Deque<MethodState> methodStates = new LinkedList<>();
    private State state = State.NORMAL;

    public State getState() {
        return state;
    }

    public void setState(State state) {
        Validate.notNull(state);
        this.state = state;
    }
    
    public void push(MethodState methodState) {
        Validate.notNull(methodState);
        methodStates.push(methodState);
    }
    
    public MethodState pop() {
        Validate.validState(!methodStates.isEmpty());
        return methodStates.pop();
    }
    
    public void yield() {
        throw new UnsupportedOperationException("Caller not instrumented");
    }
    
    public enum State {
        NORMAL,
        SAVING,
        LOADING,
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
