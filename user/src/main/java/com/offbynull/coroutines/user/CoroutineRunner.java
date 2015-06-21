/*
 * Copyright (c) 2015, Kasra Faghihi, All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.offbynull.coroutines.user;

import java.io.Serializable;

/**
 * Used to execute a {@link Coroutine}. All {@link Coroutine}s must be executed through this class.
 * @author Kasra Faghihi
 */
public final class CoroutineRunner implements Serializable {
    private static final long serialVersionUID = 2L;
    
    private Coroutine coroutine;
    private Continuation continuation = new Continuation();

    /**
     * Constructs a {@link CoroutineRunner} object.
     * @param coroutine coroutine to run
     * @throws NullPointerException if any argument is {@code null}
     */
    public CoroutineRunner(Coroutine coroutine) {
        if (coroutine == null) {
            throw new NullPointerException();
        }
        this.coroutine = coroutine;
    }

    /**
     * Starts/resumes execution of this coroutine. If the coroutine being executed reaches a suspension point (meaning that the method calls
     * {@link Continuation#suspend() }), this method will return {@code true}. If the coroutine has finished executing, this method will
     * return {@code false}.
     * <p>
     * Calling this method again after the coroutine has finished executing will restart the coroutine.
     * @return {@code false} if execution has completed (the method has return), {@code true} if execution was suspended.
     * @throws CoroutineException an exception occurred during execution of this coroutine, the saved execution stack and object state may
     * be out of sync at this point (meaning that unless you know what you're doing, you should not call {@link CoroutineRunner#execute() }
     * again)
     */
    public boolean execute() {
        try {
            coroutine.run(continuation);
            continuation.finishedExecutionCycle();
        } catch (Exception e) {
            throw new CoroutineException("Exception thrown during execution", e);
        }
        
        // if mode was not set to SAVING after return, it means the method finished executing
        if (continuation.getMode() != Continuation.MODE_SAVING) {
            continuation.reset(); // clear methodstates + set to normal, this is not explicitly nessecary at this point but set anyways
            return false;
        } else {
            continuation.setMode(Continuation.MODE_LOADING); // set to loading for next invokation
            return true;
        }
    }

    /**
     * Get the context. Accessible via the {@link Continuation} object that gets used by this coroutine.
     * @return context context
     */
    public Object getContext() {
        return continuation.getContext();
    }

    /**
     * Set the context. Accessible via the {@link Continuation} object that gets used by this coroutine.
     * @param context context
     */
    public void setContext(Object context) {
        continuation.setContext(context);
    }

    /**
     * Get the coroutine assigned to this runner.
     * @return coroutine assigned to this runner
     */
    public Coroutine getCoroutine() {
        return coroutine;
    }
    
}
