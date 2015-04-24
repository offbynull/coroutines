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
package com.offbynull.coroutines.instrumenter;

import java.util.List;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.TryCatchBlockNode;

final class ContinuationPointInstructions {
    private final AbstractInsnNode originalInvokeInsnNode;
    
    // the logic that gets executed when the switch statement at the top of the method determines that the method is being loaded
    private final InsnList restoreInsnNodes;
    
    // the logic that replaces invocation
    private final InsnList invokeReplacementInsnNodes;
    
    private final List<TryCatchBlockNode> tryCatchBlockNodes;

    ContinuationPointInstructions(
            AbstractInsnNode originalInvokeInsnNode,
            InsnList restoreInsnNodes,
            InsnList invokeReplacementInsnNodes,
            List<TryCatchBlockNode> tryCatchBlockNodes) {
        Validate.notNull(originalInvokeInsnNode);
        Validate.notNull(restoreInsnNodes);
        Validate.notNull(invokeReplacementInsnNodes);
        Validate.notNull(tryCatchBlockNodes);
        Validate.noNullElements(tryCatchBlockNodes);
        this.originalInvokeInsnNode = originalInvokeInsnNode;
        this.restoreInsnNodes = restoreInsnNodes;
        this.invokeReplacementInsnNodes = invokeReplacementInsnNodes;
        this.tryCatchBlockNodes = tryCatchBlockNodes;
    }

    // original node containing the invocation instruction to be replaced by invokeReplacementInsnNodes.
    AbstractInsnNode getOriginalInvokeInsnNode() {
        return originalInvokeInsnNode;
    }
    
    // WARNING: Be careful with using these more than once. If you insert one InsnList in to another InsnList, it'll become empty. If you
    // need to insert the instructions in an InsnList multiple times, make sure to CLONE IT FIRST!
    
    // Instructions for restoring the state of the method and continuing its execution. These instructions get inserted before any of the
    // original method code. There are no trycatch blocks covering this code.
    //
    // The operand stack at this point should be empty.
    //
    // The local variables at this point should be only...
    //
    //  1. methodState
    //  2. stack (the saved operand stack from methodState)
    //  3.localVars (the saved local variables table from methodState)
    //  4. lockState (the state of monitors/locks from methodState)
    InsnList getRestoreInsnNodes() {
        return restoreInsnNodes;
    }

    // Instructions to replace originalInvokeInsnNode. These instructions get inserted in place of originalInvokeInsnNode, meaning that
    // originalInvokeInsnNode is removed and these instructions are inserted in its place. There may be existing trycatch blocks
    // encapsulating this portion of the original method. You should be mindful of this when you're writing your instrumentation logic
    // -- specifically because if it is encapsulated in a try-catch any instruction may generate a throwable which means your stack and
    // localvariables need to be in sync.
    //
    // The operand stack at this point should be whatever it was when the original method was invoked.
    //
    // All original local variables that were available at this point should still be available.
    InsnList getInvokeReplacementInsnNodes() {
        return invokeReplacementInsnNodes;
    }

    // New trycatchblock segments generated for restoreInsnNodes/invokeReplacementInsnNodes.
    List<TryCatchBlockNode> getTryCatchBlockNodes() {
        return tryCatchBlockNodes;
    }
    
}
