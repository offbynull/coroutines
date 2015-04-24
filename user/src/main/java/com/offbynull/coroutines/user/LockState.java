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
import java.util.ListIterator;

/**
 * Do not use -- for internal use only.
 * <p>
 * Holds on to the state of "synchronized" locks (MONITORENTER/MONITOREXIT) within a method frame.
 * @author Kasra Faghihi
 */
public final class LockState implements Serializable {
    private static final long serialVersionUID = 2L;

    // We use a linkedlist to make sure that we retain the order of monitors as they come in. Otherwise we're going to deal with deadlock
    // issues if we have code structured with double locks. For example, imagine the following scenario...
    //
    // Method 1:
    // synchronized(a) {
    //    synchronized(b) {
    //        continuation.suspend();
    //    }
    // }
    //
    // Method 2 (same as method1):
    // synchronized(a) {
    //    synchronized(b) {
    //        continuation.suspend();
    //    }
    // }
    //
    //
    // If we weren't ordered, we may end up coming back after a suspend and re-locking the monitors in the wrong order. For example,
    // we may lock b before we lock a. That has the potential for a deadlock because another thread could execute the locking logic
    // correctly (first a and then b). Dual locking without retaining the same order = a deadlock waiting to happen.
    //
    // Long story short: it's vital that we keep the order which locks happen
    private LinkedList monitors = new LinkedList();

    /**
     * Do not use -- for internal use only.
     * <p>
     * Should be called after a MONITORENTER instruction has been executed. Tracks the object that MONITORENTER was used on.
     * @param monitor object the MONITORENTER instruction was used on
     */
    public void enter(Object monitor) {
        if (monitor == null) {
            throw new NullPointerException();
        }

        monitors.add(monitor);
    }

    /**
     * Do not use -- for internal use only.
     * <p>
     * Should be called after a MONITOREXIT instruction has been executed. Untracks the object that MONITOREXIT was used on.
     * @param monitor object the MONITOREXIT instruction was used on
     */
    public void exit(Object monitor) {
        if (monitor == null) {
            throw new NullPointerException();
        }

        // remove last
        ListIterator it = monitors.listIterator(monitors.size());
        while (it.hasPrevious()) {
            Object prev = it.previous();
            if (monitor == prev) { // Never use equals() to test equality. We always need to make sure that the objects are the same, we
                                   // don't care if they're the objects are logically equivalent
                it.remove();
                return;
            }
        }
        
        throw new IllegalArgumentException(); // not found
    }
    
    /**
     * Dumps monitors out as an array. Order is retained.
     * @return monitors
     */
    public Object[] toArray() {
        return monitors.toArray();
    }
}
