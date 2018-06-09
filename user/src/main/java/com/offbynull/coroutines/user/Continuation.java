/*
 * Copyright (c) 2017, Kasra Faghihi, All rights reserved.
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
 * This class is used to store and restore the execution state. Any method that takes in this type as a parameter will be instrumented to
 * have its state saved/restored.
 * <p>
 * Calls to {@link #suspend() } will suspend/yield the execution of the coroutine. Calls to {@link #setContext(java.lang.Object) } /
 * {@link #getContext() } can be used to pass data back and forth between the coroutine and its caller. <b>All other methods are for
 * internal use by the instrumentation logic and should not be used directly.</b>.
 * @author Kasra Faghihi
 */
public final class Continuation implements Serializable {
    private static final long serialVersionUID = 6L;
    
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
    
    private MethodState firstPointer;
    
    private MethodState nextLoadPointer;
    private MethodState nextUnloadPointer;

    private MethodState firstCutpointPointer;
    
    private int mode = MODE_NORMAL;
    private Object context;

    // How should method states be handled? Imagine that we started off restoring the following call chain...
    // runA() <-- firstPointer[0]
    //  runB() <-- firstPointer[1]
    //   runC() <-- firstPointer[2]
    //    runD() <-- firstPointer[3]
    //     runE() <-- firstPointer[4]
    //
    // After the restore finishes, the following happens...
    // 1. runE() finishes running and returns
    // 2. then, runD() finishes running and returns
    // 3. then, runC() decided to call runX()
    // 4. then, runX() decided to call runY()
    // 5. then, runY() decides to call Continuation.suspend()
    //
    // So we left the bottom 2 existing method frames and entered in to 2 new method frames, and the callstack would now look like this...
    // runA()
    //  runB()
    //   runC()
    //    runX()
    //     runY()
    //
    //
    // Things happen in phases.
    //
    // PHASE1
    // ------
    // The first phase is loading. We call loadNextMethodState() to get the method state for the next method in the call chain. Pretty
    // straight forward.. runA -> runB -> runC -> runD -> runE.
    //
    //
    // PHASE2
    // ------
    // Once things are loaded... as we leave the restored continuation points in runE() and runD(), we call unloadCurrentMethodState() to
    // mark these method states as invalid. So after runE()+runD() return, we should be pointing to runC(). Everything after it is no longer
    // valid...
    //
    // runA() <-- firstPointer[0]
    //  runB() <-- firstPointer[1]
    //   runC() <-- firstPointer[2] / nextUnloadPointer
    //    runD() <-- firstPointer[3] (NO LONGER CONSIDERED VALID, BUT KEPT ANYWAS -- EXPLAINED FURTHER ON)
    //     runE() <-- firstPointer[4] (NO LONGER CONSIDERED VALID, BUT KEPT ANYWAS -- EXPLAINED FURTHER ON)
    //
    //
    // PHASE3
    // ------
    // As runX() and runY() suspend, they put their own method states as part of a NEW linked list: firstCutpointPointer. They do this by
    // calling pushNewMethodState().
    //   !!!WE ONLY CREATE METHOD STATES AND ADD THEM TO THIS NEW LIST AFTER THEY'RE SUSPEND! THIS IS REALLY IMPORTANT TO REMEMBER!!!
    //
    // runX() <-- firstCutpointPointer[0]
    //  runY()  <-- firstCutpointPointer[1]
    //
    //
    // Then, once we successfully make our way up and out of the callstack, we merge these two lists together...
    // runA() <-- savedMethodState[0]
    //  runB() <-- savedMethodState[1]
    //   runC() <-- savedMethodState[2] / nextUnloadPointer
    //    runX() <-- savedMethodState[3] / firstCutpointPointer[0]
    //     runY() <-- savedMethodState[4] / firstCutpointPointer[1]
    //
    //
    // Why do we use a separate list for new invocations (postCutPointMethodState)? Because if there's an uncaught exception, we
    // still want to keep the old one exactly the way it was. That's why technically we kept runD() and runE()s method states and just
    // shift around the pointers. It's only after we're successfuly that we "commit the changes".
    //
    //
    // ADDITIONAL NOTES
    // ----------------
    // These phases should always be done in order. If you don't do them in order (e.g. if you try to unloadCurrentMethodState() after
    // you've called pushNewMethodState()), things will likely not act right.
    
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
        // if (!(mode == MODE_NORMAL || mode == MODE_SAVING || mode == MODE_LOADING)) {
        //    throw new IllegalArgumentException();
        // }
        this.mode = mode;
    }







    /**
     * Do not use -- for internal use only.
     * @return n/a
     */
    public MethodState loadNextMethodState() {
        MethodState ret = nextLoadPointer;
        nextLoadPointer = nextLoadPointer.getNext();
        
        // We've reached the end of load list, so set up the 'unload' list that gets called when a method continues execution from the point
        // where it's paused it.
        if (nextLoadPointer == null) {
            nextUnloadPointer = ret;
        }
        
        return ret;
    }

    /**
     * Do not use -- for internal use only.
     */
    public void unloadCurrentMethodState() {
        nextUnloadPointer = nextUnloadPointer.getPrevious();
    }

    /**
     * Do not use -- for internal use only.
     * @param methodState n/a
     */
    public void unloadMethodStateToBefore(MethodState methodState) {
        // REMEMBER: methodState being passed in must be in the linkedlist starting from firstPointer
        
        //if (methodState == null) {
        //    throw new NullPointerException();
        //}
        nextUnloadPointer = methodState.getPrevious();
    }

    /**
     * Do not use -- for internal use only.
     * @param methodState n/a
     */
    public void pushNewMethodState(MethodState methodState) {
        //if (methodState == null) {
        //    throw new NullPointerException();
        //}

        methodState.setNext(firstCutpointPointer);
        firstCutpointPointer = methodState;
    }

    /**
     * Do not use -- for internal use only.
     */
    public void reset() {
        firstPointer = null;
        nextLoadPointer = null;
        nextUnloadPointer = null;
        firstCutpointPointer = null;
        mode = MODE_NORMAL;
    }

    /**
     * Do not use -- for internal use only.
     */
    public void successExecutionCycle() {
        // FOR A PRIMER ON WHAT WE'RE DOING HERE, SEE LARGE BLOCK OF COMMENT AT BEGINNING OF CLASS

        if (nextUnloadPointer != null) {
            nextUnloadPointer.setNext(firstCutpointPointer);
        } else {
            firstPointer = firstCutpointPointer;
        }
        
        nextLoadPointer = firstPointer;     // reset next load pointer so we load from the beginning
        nextUnloadPointer = null;           // reset unload pointer
        firstCutpointPointer = null;        // reset cutpoint list
    }

    /**
     * Do not use -- for internal use only.
     */
    public void failedExecutionCycle() {
        // FOR A PRIMER ON WHAT WE'RE DOING HERE, SEE LARGE BLOCK OF COMMENT AT BEGINNING OF CLASS
        
        nextLoadPointer = firstPointer;     // reset next load pointer so we load from the beginning
        nextUnloadPointer = null;           // reset unload pointer
        firstCutpointPointer = null;        // reset cutpoint list
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
    
    
    
    
    
    
    
    
    /**
     * Do not use -- for internal use only. For testing.
     * @param idx n/a
     * @return n/a
     */
    public MethodState getSaved(int idx) {
        if (idx < 0) {
            throw new IllegalArgumentException();
        }

        MethodState state = firstPointer;
        for (int i = 0; i < idx; i++) {
            state = state.getNext();
            if (state == null) {
               throw new IllegalArgumentException();
            }
        }
        return state;
    }

    /**
     * Do not use -- for internal use only. For testing.
     * @return n/a
     */
    public int getSize() {
        int ret = 0;
        MethodState state = firstPointer;
        while (state != null) {
            ret++;
            state = state.getNext();
        }
        return ret;
    }
}
