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

import java.util.HashMap;
import java.util.Map;

/**
 * Do not use -- for internal use only.
 * <p>
 * Holds on to the state of "synchronized" locks (MONITORENTER/MONITOREXIT) within a method frame.
 * @author Kasra Faghihi
 */
public final class LockState {

    private Map monitors = new HashMap();

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

        Counter counter = (Counter) monitors.get(monitor);
        if (counter == null) {
            counter = new Counter();
            monitors.put(monitor, counter);
        }

        counter.increment();
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

        Counter counter = (Counter) monitors.get(monitor);
        if (counter == null) {
            throw new IllegalArgumentException();
        }

        counter.decrement();
        if (counter.isZero()) {
            Object removedObject = monitors.remove(monitor);
            if (removedObject == null) {
                throw new IllegalArgumentException();
            }
        }
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

    private static final class Counter {

        private int count;

        public void increment() {
            if (count == Integer.MAX_VALUE) { // sanity check
                throw new IllegalStateException();
            }
            count++;
        }

        public void decrement() {
            if (count == 0) { // sanity check
                throw new IllegalStateException();
            }
            count--;
        }

        public boolean isZero() {
            return count == 0;
        }

        public int getCount() {
            return count;
        }
    }
}
