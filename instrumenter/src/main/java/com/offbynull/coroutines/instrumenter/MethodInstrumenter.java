/*
 * Copyright (c) 2016, Kasra Faghihi, All rights reserved.
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

import static com.offbynull.coroutines.instrumenter.ContinuationGenerators.entryPointLoader;
import static com.offbynull.coroutines.instrumenter.ContinuationGenerators.saveState;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static com.offbynull.coroutines.instrumenter.SynchronizationGenerators.enterMonitorAndStore;
import static com.offbynull.coroutines.instrumenter.SynchronizationGenerators.exitMonitorAndDelete;
import com.offbynull.coroutines.instrumenter.generators.DebugGenerators.MarkerType;
import static com.offbynull.coroutines.user.MethodState.getIdentifyingFieldName;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

final class MethodInstrumenter {
    
    // The following consts are used to write out the versions of the methods being instrumented -- this is used by the serialization logic
    // to determine if the MethodState objects being deserialized are for the methods loaded.
    private static final int INSTRUMENTED_METHODID_FIELD_ACCESS = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_STATIC;
    private static final Type INSTRUMENTED_METHODID_FIELD_TYPE = Type.INT_TYPE;
    private static final Integer INSTRUMENTED_METHODID_FIELD_VALUE = 0;

    public void instrument(ClassNode classNode, MethodNode methodNode, MethodAttributes attrs) {
        Validate.notNull(classNode);
        Validate.notNull(methodNode);
        Validate.notNull(attrs);

        // Sanity check to make sure that the method belongs to the class
        Validate.isTrue(classNode.methods.contains(methodNode));
        
        // These sanity checks need to exist. The methodNode.instructions.insertBefore/remove methods don't actually check to make sure the
        // instructions they're operating on belong to the method. This is here to make sure that the properties and methodNode match.
        attrs.getContinuationPoints().stream()
                .map(x -> x.getInvokeInstruction())
                .forEach(x -> Validate.isTrue(methodNode.instructions.contains(x)));
        attrs.getSynchronizationPoints().stream()
                .map(x -> x.getMonitorInstruction())
                .forEach(x -> Validate.isTrue(methodNode.instructions.contains(x)));

        // Add trycatch nodes
        attrs.getContinuationPoints().stream()
                .filter(x -> x instanceof TryCatchInvokeContinuationPoint)
                .map(x -> (TryCatchInvokeContinuationPoint) x)
                .map(x -> x.getTryCatchBlock())
                .forEach(x -> methodNode.tryCatchBlocks.add(0, x));
        
        // Add loading code (this includes continuation restore points)
        InsnList entryPoint = entryPointLoader(attrs);
        methodNode.instructions.insert(entryPoint);
        
        // Add continuation save points
        List<ContinuationPoint> continuationPoints = attrs.getContinuationPoints();
        for (int i = 0; i < continuationPoints.size(); i++) {
            ContinuationPoint cp = continuationPoints.get(i);

            AbstractInsnNode nodeToReplace = cp.getInvokeInstruction();
            InsnList insnsToReplaceWith = saveState(attrs, i);
            
            methodNode.instructions.insertBefore(nodeToReplace, insnsToReplaceWith);
            methodNode.instructions.remove(nodeToReplace);
        }
        
        // Add synchronization save points
        List<SynchronizationPoint> synchPoints = attrs.getSynchronizationPoints();
        MarkerType markerType = attrs.getSettings().getMarkerType();
        LockVariables lockVars = attrs.getLockVariables();
        for (int i = 0; i < synchPoints.size(); i++) {
            SynchronizationPoint sp = synchPoints.get(i);

            InsnNode nodeToReplace = sp.getMonitorInstruction();
            InsnList insnsToReplaceWith;
            switch (nodeToReplace.getOpcode()) {
                case Opcodes.MONITORENTER:
                    insnsToReplaceWith = enterMonitorAndStore(markerType, lockVars);
                    break;
                case Opcodes.MONITOREXIT:
                    insnsToReplaceWith = exitMonitorAndDelete(markerType, lockVars);
                    break;
                default:
                    throw new IllegalStateException(); //should never happen
            }
            
            methodNode.instructions.insertBefore(nodeToReplace, insnsToReplaceWith);
            methodNode.instructions.remove(nodeToReplace);
        }
        
        // Shove in the deserialization identification data as a fields on the class -- used by deserialization feature. 
        int methodId = attrs.getSignature().getMethodId();
        for (int i = 0; i < continuationPoints.size(); i++) {
            int continuationPointId = i;
            FieldNode methodIdField = new FieldNode(
                    INSTRUMENTED_METHODID_FIELD_ACCESS,
                    getIdentifyingFieldName(methodId, continuationPointId),
                    INSTRUMENTED_METHODID_FIELD_TYPE.getDescriptor(),
                    null,
                    INSTRUMENTED_METHODID_FIELD_VALUE);
            classNode.fields.add(methodIdField);
        }
    }
}
