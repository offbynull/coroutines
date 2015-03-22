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

import java.util.LinkedList;
import java.util.ListIterator;

/**
 * Do not use -- for internal use only.
 * <p>
 * Holds on to the state of "synchronized" locks (MONITORENTER/MONITOREXIT) within a method frame.
 * @author Kasra Faghihi
 */
public final class LockState {

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

        WrappedObject wrappedMonitor = new WrappedObject(monitor);
        monitors.add(wrappedMonitor);
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
        WrappedObject wrappedMonitor = new WrappedObject(monitor);
        ListIterator it = monitors.listIterator(monitors.size());
        while (it.hasPrevious()) {
            Object prev = it.previous();
            if (wrappedMonitor.equals(prev)) {
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

    private static final class WrappedObject {

        private final Object object;

        public WrappedObject(Object object) {
            if (object == null) {
                throw new NullPointerException();
            }
            this.object = object;
        }

        public int hashCode() {
            return System.identityHashCode(object); // We don't want the original hashCode() on the object field. The hashCode() method may
                                                    // have been overwritten along with the equals() method for logical equivalence. We
                                                    // don't care about logical equivallency in this case, we just care about if the objects
                                                    // being compared are the same object.
        }

        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final WrappedObject other = (WrappedObject) obj;
            if (this.object != other.object) { // We don't ever want to call the equals() method on the object field. We're trying to track
                                               // objects being used as monitors, so logical equivalence is not what we want. We want to
                                               // make sure that the two objects are the same actual object, not that they're logically
                                               // equivallent.
                return false;
            }
            return true;
        }

    }
}
