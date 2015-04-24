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

    AbstractInsnNode getOriginalInvokeInsnNode() {
        return originalInvokeInsnNode;
    }
    
    // WARNING: Be careful with using these more than once. If you insert one InsnList in to another InsnList, it'll become empty. If you
    // need to insert the instructions in an InsnList multiple times, make sure to CLONE IT FIRST!
    
    InsnList getRestoreInsnNodes() {
        return restoreInsnNodes;
    }

    InsnList getInvokeReplacementInsnNodes() {
        return invokeReplacementInsnNodes;
    }

    List<TryCatchBlockNode> getTryCatchBlockNodes() {
        return tryCatchBlockNodes;
    }
    
}
