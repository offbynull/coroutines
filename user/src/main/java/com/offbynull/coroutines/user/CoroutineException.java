package com.offbynull.coroutines.user;

public class CoroutineException extends RuntimeException {

    public CoroutineException() {
    }

    public CoroutineException(String message) {
        super(message);
    }

    public CoroutineException(String message, Throwable cause) {
        super(message, cause);
    }

    public CoroutineException(Throwable cause) {
        super(cause);
    }
    
}
