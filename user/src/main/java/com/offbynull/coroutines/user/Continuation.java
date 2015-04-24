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
import java.util.LinkedList;

/**
 * This class is used to store and restore the execution state. Any method that takes in this type as a parameter will be instrumented to
 * have its state saved/restored.
 * <p>
 * Calls to {@link #suspend() } will suspend/yield the execution of the coroutine. Calls to {@link #setContext(java.lang.Object) } /
 * {@link #getContext() } can be used to pass data back and forth between the coroutine and its caller. <b>All other methods are for
 * internal use by the instrumentation logic and should not be used directly.</b>.
 * @author Kasra Faghihi
 */
public final class Continuation implements Serializable {
    private static final long serialVersionUID = 2L;
    
    /**
     * Do not use -- for internal use only.
     */
    public static final int MODE_NORMAL = 0;
    /**
     * Do not use -- for internal use only.
     */
    public static final int MODE_SAVING = 1;
    /**
     * Do not use -- for internal use only.
     */
    public static final int MODE_LOADING = 2;
    private LinkedList savedMethodStates = new LinkedList();
    private LinkedList pendingMethodStates = new LinkedList();
    private int mode = MODE_NORMAL;
    
    private Object context;

    Continuation() {
        // do nothing
    }
    
    /**
     * Do not use -- for internal use only.
     * @return n/a
     */
    public int getMode() {
        return mode;
    }

    /**
     * Do not use -- for internal use only.
     * @param mode n/a
     */
    public void setMode(int mode) {
        if (!(mode == MODE_NORMAL || mode == MODE_SAVING || mode == MODE_LOADING)) {
            throw new IllegalArgumentException();
        }
        this.mode = mode;
    }

    /**
     * Do not use -- for internal use only.
     * @param max n/a
     */
    public void clearExcessPending(int max) {
        // This is for handling cases where the pendingMethodStates list has more items in it than it should. This happens when you call in
        // to a method that throws an exception. Because that exception is thrown, removeLastPending() is never called when it leaves the
        // method, which means that pendingMethodStates will contain extra MethodState objects that should have been removed.
        //
        // Imagine the following scenario
        //
        //    public void run(Continuation c) {
        //
        //        try {
        //            new InnerClass1().run(c); // THROWS AN EXCEPTION
        //        } catch (RuntimeException re) {
        //            System.out.println("Caught exception and continuing " + re);
        //        }
        //
        //      // AT THIS POINT, pendingMethodStates WILL CONTAIN EXTRA ITEMS BECAUSE THE InnerClass1.run() THREW AN EXCEPTION AND
        //        new InnerClass2().run(c);
        //    }
        //
        //    private final class InnerClass1 {
        //        public void run(Continuation c) {
        //            c.suspend();
        //            throw expectedException;
        //        }
        //    }
        //
        //    private static final class InnerClass2 {
        //        public void run(Continuation c) {
        //            c.suspend();
        //        }
        //    }
        if (max < 0) {
            throw new IllegalArgumentException();
        }
        
        for (int i = pendingMethodStates.size() - max; i > 0; i--) {
            pendingMethodStates.removeLast();
        }
    }

    /**
     * Do not use -- for internal use only.
     * @param methodState n/a
     */
    public void addPending(MethodState methodState) {
        if (methodState == null) {
            throw new NullPointerException();
        }        
        pendingMethodStates.addLast(methodState);
    }

    /**
     * Do not use -- for internal use only.
     * @return n/a
     */
    public int getPendingSize() {
        return pendingMethodStates.size();
    }
    
    /**
     * Do not use -- for internal use only.
     * @return n/a
     */
    public MethodState removeFirstSaved() {
        if (savedMethodStates.isEmpty()) {
            throw new IllegalStateException();
        }
        return (MethodState) savedMethodStates.removeFirst();
    }

    /**
     * Do not use -- for internal use only. For testing.
     * @param idx n/a
     * @return n/a
     */
    public MethodState getSaved(int idx) {
        return (MethodState) savedMethodStates.get(idx);
    }
    /**
     * Do not use -- for internal use only.
     */
    public void reset() {
        pendingMethodStates.clear();
        savedMethodStates.clear();
        mode = MODE_NORMAL;
    }

    /**
     * Do not use -- for internal use only.
     */
    public void finishedExecutionCycle() {
        savedMethodStates = pendingMethodStates;
        pendingMethodStates = new LinkedList();
    }
    
    /**
     * Call to suspend/yield execution.
     * @throws UnsupportedOperationException if the caller has not been instrumented
     */
    public void suspend() {
        throw new UnsupportedOperationException("Caller not instrumented");
    }

    /**
     * Get the context.
     * @return context
     */
    public Object getContext() {
        return context;
    }

    /**
     * Set the context.
     * @param context context
     */
    public void setContext(Object context) {
        this.context = context;
    }
    
}
