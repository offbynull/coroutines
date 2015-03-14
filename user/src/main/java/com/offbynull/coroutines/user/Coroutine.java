package com.offbynull.coroutines.user;

public final class Coroutine {
    private Continuation continuation;
    
    public Continuation ready() {
        if (continuation == null) {
            continuation = new Continuation();
        } else {
            continuation.setMode(Continuation.MODE_LOADING);
        }
        
        return continuation;
    }
}
