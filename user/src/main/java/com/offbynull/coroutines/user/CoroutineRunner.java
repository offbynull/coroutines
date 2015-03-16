package com.offbynull.coroutines.user;

public final class CoroutineRunner {
    private Coroutine coroutine;
    private Continuation continuation = new Continuation();

    public CoroutineRunner(Coroutine coroutine) {
        if (coroutine == null) {
            throw new NullPointerException();
        }
        this.coroutine = coroutine;
    }
    
    public boolean execute() throws Exception {
        coroutine.run(continuation);
        
        // if mode was not set to SAVING after return, it means the method finished executing
        if (continuation.getMode() != Continuation.MODE_SAVING) {
            continuation.reset(); // clear methodstates + set to normal, this is not explicitly nessecary at this point but set anyways
            return false;
        } else {
            continuation.replaceSavedMethodStates();
            continuation.setMode(Continuation.MODE_LOADING); // set to loading for next invokation
            return true;
        }
    }
}
