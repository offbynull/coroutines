package com.offbynull.coroutines.user;

public final class Coroutine {
    private Continuation continuation = new Continuation();
    
    public Continuation ready() {
        continuation.setMode(continuation.isEmpty() ? Continuation.MODE_NORMAL : Continuation.MODE_LOADING);
        return continuation;
    }
}
