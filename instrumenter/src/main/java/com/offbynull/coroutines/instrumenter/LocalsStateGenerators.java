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

import static com.offbynull.coroutines.instrumenter.AllocationGenerators.allocateDoubleArray;
import static com.offbynull.coroutines.instrumenter.AllocationGenerators.allocateFloatArray;
import static com.offbynull.coroutines.instrumenter.AllocationGenerators.allocateIntArray;
import static com.offbynull.coroutines.instrumenter.AllocationGenerators.allocateLongArray;
import static com.offbynull.coroutines.instrumenter.AllocationGenerators.allocateObjectArray;
import static com.offbynull.coroutines.instrumenter.AllocationGenerators.freeDoubleArray;
import static com.offbynull.coroutines.instrumenter.AllocationGenerators.freeFloatArray;
import static com.offbynull.coroutines.instrumenter.AllocationGenerators.freeIntArray;
import static com.offbynull.coroutines.instrumenter.AllocationGenerators.freeLongArray;
import static com.offbynull.coroutines.instrumenter.AllocationGenerators.freeObjectArray;
import com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable;
import com.offbynull.coroutines.instrumenter.generators.DebugGenerators.MarkerType;
import static com.offbynull.coroutines.instrumenter.generators.DebugGenerators.debugMarker;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

/**
 * Utility class to generate bytecode instructions that save/load the local variables table.
 * @author Kasra Faghihi
 */
final class LocalsStateGenerators {
    private LocalsStateGenerators() {
        // do nothing
    }

    /**
     * Generates instructions to load the local variables table.
     * @param settings instrumenter settings
     * @param storageVars variables to load locals from
     * @param frame execution frame at the instruction for which the local variables table is to be restored
     * @return instructions to load the local variables table from an array
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList loadLocals(InstrumentationSettings settings, StorageVariables storageVars, Frame<BasicValue> frame) {
        Validate.notNull(settings);
        Validate.notNull(storageVars);
        Validate.notNull(frame);
        
        MarkerType markerType = settings.getMarkerType();

        Variable intsVar = storageVars.getIntStorageVar();
        Variable floatsVar = storageVars.getFloatStorageVar();
        Variable longsVar = storageVars.getLongStorageVar();
        Variable doublesVar = storageVars.getDoubleStorageVar();
        Variable objectsVar = storageVars.getObjectStorageVar();
        
        int intsCounter = 0;
        int floatsCounter = 0;
        int longsCounter = 0;
        int doublesCounter = 0;
        int objectsCounter = 0;

        
        InsnList ret = new InsnList();
        
        // Load the locals
        ret.add(debugMarker(markerType, "Loading locals"));
        for (int i = 0; i < frame.getLocals(); i++) {
            BasicValue basicValue = frame.getLocal(i);
            Type type = basicValue.getType();

            // If type == null, basicValue is pointing to uninitialized var -- basicValue.toString() will return ".". This means that this
            // slot contains nothing to load. So, skip this slot if we encounter it (such that it will remain uninitialized).
            if (type == null) {
                ret.add(debugMarker(markerType, "Skipping uninitialized value at " + i));
                continue;
            }
            
            // If type is 'Lnull;', this means that the slot has been assigned null and that "there has been no merge yet that would 'raise'
            // the type toward some class or interface type" (from ASM mailing list). We know this slot will always contain null at this
            // point in the code so there's no specific value to load up from the array. Instead we push a null in to that slot, thereby
            // keeping the same 'Lnull;' type originally assigned to that slot (it doesn't make sense to do a CHECKCAST because 'null' is
            // not a real class and can never be a real class -- null is a reserved word in Java).
            if (type.getSort() == Type.OBJECT && "Lnull;".equals(type.getDescriptor())) {
                ret.add(debugMarker(markerType, "Putting null value at " + i));
                ret.add(new InsnNode(Opcodes.ACONST_NULL));
                ret.add(new VarInsnNode(Opcodes.ASTORE, i));
                continue;
            }

            // Load the locals
            switch (type.getSort()) {
                case Type.BOOLEAN:
                case Type.BYTE:
                case Type.SHORT:
                case Type.CHAR:
                case Type.INT:
                    ret.add(debugMarker(markerType, "Loading int to LVT index " + i + " from storage index " + intsCounter));
                    ret.add(new VarInsnNode(Opcodes.ALOAD, intsVar.getIndex()));    // [int[]]
                    ret.add(new LdcInsnNode(intsCounter));                          // [int[], idx]
                    ret.add(new InsnNode(Opcodes.IALOAD));                          // [val]
                    ret.add(new VarInsnNode(Opcodes.ISTORE, i));                    // []
                    intsCounter++;
                    break;
                case Type.FLOAT:
                    ret.add(debugMarker(markerType, "Loading float to LVT index " + i + " from storage index " + floatsCounter));
                    ret.add(new VarInsnNode(Opcodes.ALOAD, floatsVar.getIndex()));  // [float[]]
                    ret.add(new LdcInsnNode(floatsCounter));                        // [float[], idx]
                    ret.add(new InsnNode(Opcodes.FALOAD));                          // [val]
                    ret.add(new VarInsnNode(Opcodes.FSTORE, i));                    // []
                    floatsCounter++;
                    break;
                case Type.LONG:
                    ret.add(debugMarker(markerType, "Loading long to LVT index " + i + " from storage index " + longsCounter));
                    ret.add(new VarInsnNode(Opcodes.ALOAD, longsVar.getIndex()));   // [long[]]
                    ret.add(new LdcInsnNode(longsCounter));                         // [long[], idx]
                    ret.add(new InsnNode(Opcodes.LALOAD));                          // [val_PART1, val_PART2]
                    ret.add(new VarInsnNode(Opcodes.LSTORE, i));                    // []
                    longsCounter++;
                    break;
                case Type.DOUBLE:
                    ret.add(debugMarker(markerType, "Loading double to LVT index " + i + " from storage index " + doublesCounter));
                    ret.add(new VarInsnNode(Opcodes.ALOAD, doublesVar.getIndex())); // [double[]]
                    ret.add(new LdcInsnNode(doublesCounter));                       // [double[], idx]
                    ret.add(new InsnNode(Opcodes.DALOAD));                          // [val_PART1, val_PART2]
                    ret.add(new VarInsnNode(Opcodes.DSTORE, i));                    // []
                    doublesCounter++;
                    break;
                case Type.ARRAY:
                case Type.OBJECT:
                    ret.add(debugMarker(markerType, "Loading object to LVT index " + i + " from storage index " + objectsCounter));
                    ret.add(new VarInsnNode(Opcodes.ALOAD, objectsVar.getIndex())); // [Object[]]
                    ret.add(new LdcInsnNode(objectsCounter));                       // [Object[], idx]
                    ret.add(new InsnNode(Opcodes.AALOAD));                          // [val]
                    // must cast, otherwise the jvm won't know the type that's in the localvariable slot and it'll fail when the code tries
                    // to access a method/field on it
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, basicValue.getType().getInternalName()));
                    ret.add(new VarInsnNode(Opcodes.ASTORE, i));                    // []
                    objectsCounter++;
                    break;
                case Type.METHOD:
                case Type.VOID:
                default:
                    throw new IllegalStateException();
            }
        }
        
        return ret;
    }
    
    /**
     * Generates instructions to save the local variables table.
     * @param settings instrumenter settings
     * @param storageVars variables to store locals in to
     * @param frame execution frame at the instruction where the local variables table is to be saved
     * @return instructions to save the local variables table in to an array
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList saveLocals(InstrumentationSettings settings, StorageVariables storageVars, Frame<BasicValue> frame) {
        Validate.notNull(settings);
        Validate.notNull(storageVars);
        Validate.notNull(frame);
        
        MarkerType markerType = settings.getMarkerType();

        Variable intsVar = storageVars.getIntStorageVar();
        Variable floatsVar = storageVars.getFloatStorageVar();
        Variable longsVar = storageVars.getLongStorageVar();
        Variable doublesVar = storageVars.getDoubleStorageVar();
        Variable objectsVar = storageVars.getObjectStorageVar();
        
        int intsCounter = 0;
        int floatsCounter = 0;
        int longsCounter = 0;
        int doublesCounter = 0;
        int objectsCounter = 0;

        InsnList ret = new InsnList();

        ret.add(debugMarker(markerType, "Saving locals"));

        // Save the locals
        for (int i = 0; i < frame.getLocals(); i++) {
            BasicValue basicValue = frame.getLocal(i);
            Type type = basicValue.getType();

            // If type == null, basicValue is pointing to uninitialized var -- basicValue.toString() will return '.'. This means that this
            // slot contains nothing to save. So, skip this slot if we encounter it.
            if (type == null) {
                ret.add(debugMarker(markerType, "Skipping uninitialized value at " + i));
                continue;
            }
            
            // If type is 'Lnull;', this means that the slot has been assigned null and that "there has been no merge yet that would 'raise'
            // the type toward some class or interface type" (from ASM mailing list). We know this slot will always contain null at this
            // point in the code so we can avoid saving it. When we load it back up, we can simply push a null in to that slot, thereby
            // keeping the same 'Lnull;' type.
            if ("Lnull;".equals(type.getDescriptor())) {
                ret.add(debugMarker(markerType, "Skipping null value at " + i));
                continue;
            }

            // Place item in to appropriate storage array
            switch (type.getSort()) {
                case Type.BOOLEAN:
                case Type.BYTE:
                case Type.SHORT:
                case Type.CHAR:
                case Type.INT:
                    ret.add(debugMarker(markerType, "Inserting int at LVT index " + i + " to storage index " + intsCounter));
                    ret.add(new VarInsnNode(Opcodes.ALOAD, intsVar.getIndex())); // [int[]]
                    ret.add(new LdcInsnNode(intsCounter));                       // [int[], idx]
                    ret.add(new VarInsnNode(Opcodes.ILOAD, i));                  // [int[], idx, val]
                    ret.add(new InsnNode(Opcodes.IASTORE));                      // []
                    intsCounter++;
                    break;
                case Type.FLOAT:
                    ret.add(debugMarker(markerType, "Inserting float at LVT index " + i + " to storage index " + floatsCounter));
                    ret.add(new VarInsnNode(Opcodes.ALOAD, floatsVar.getIndex())); // [float[]]
                    ret.add(new LdcInsnNode(floatsCounter));                       // [float[], idx]
                    ret.add(new VarInsnNode(Opcodes.FLOAD, i));                    // [float[], idx, val]
                    ret.add(new InsnNode(Opcodes.FASTORE));                        // []
                    floatsCounter++;
                    break;
                case Type.LONG:
                    ret.add(debugMarker(markerType, "Inserting long at LVT index " + i + " to storage index " + longsCounter));
                    ret.add(new VarInsnNode(Opcodes.ALOAD, longsVar.getIndex())); // [long[]]
                    ret.add(new LdcInsnNode(longsCounter));                       // [long[], idx]
                    ret.add(new VarInsnNode(Opcodes.LLOAD, i));                   // [long[], idx, val]
                    ret.add(new InsnNode(Opcodes.LASTORE));                       // []
                    longsCounter++;
                    break;
                case Type.DOUBLE:
                    ret.add(debugMarker(markerType, "Inserting double at LVT index " + i + " to storage index " + doublesCounter));
                    ret.add(new VarInsnNode(Opcodes.ALOAD, doublesVar.getIndex())); // [double[]]
                    ret.add(new LdcInsnNode(doublesCounter));                       // [double[], idx]
                    ret.add(new VarInsnNode(Opcodes.DLOAD, i));                     // [double[], idx, val]
                    ret.add(new InsnNode(Opcodes.DASTORE));                         // []
                    doublesCounter++;
                    break;
                case Type.ARRAY:
                case Type.OBJECT:
                    ret.add(debugMarker(markerType, "Inserting object at LVT index " + i + " to storage index " + objectsCounter));
                    ret.add(new VarInsnNode(Opcodes.ALOAD, objectsVar.getIndex())); // [Object[]]
                    ret.add(new LdcInsnNode(objectsCounter));                       // [Object[], idx]
                    ret.add(new VarInsnNode(Opcodes.ALOAD, i));                     // [Object[], idx, val]
                    ret.add(new InsnNode(Opcodes.AASTORE));                         // []
                    objectsCounter++;
                    break;
                case Type.METHOD:
                case Type.VOID:
                default:
                    throw new IllegalStateException();
            }
        }
        
        return ret;
    }
    
    /**
     * Generates instructions to create the storage arrays required for storing the local variables table.
     * @param settings instrumenter settings
     * @param contVar variable containing the continuation object
     * @param storageVars variables to store locals in to
     * @param frame execution frame at the instruction where the local variables table is to be saved
     * @return instructions to allocate the locals storage arrays
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList allocateLocalsStorageArrays(InstrumentationSettings settings, Variable contVar, StorageVariables storageVars,
            Frame<BasicValue> frame) {
        Validate.notNull(settings);
        Validate.notNull(contVar);
        Validate.notNull(storageVars);
        Validate.notNull(frame);
        
        MarkerType markerType = settings.getMarkerType();
        
        Variable intsVar = storageVars.getIntStorageVar();
        Variable floatsVar = storageVars.getFloatStorageVar();
        Variable longsVar = storageVars.getLongStorageVar();
        Variable doublesVar = storageVars.getDoubleStorageVar();
        Variable objectsVar = storageVars.getObjectStorageVar();

        StorageSizes storageSizes = computeSizes(frame);
        
        InsnList ret = new InsnList();
        ret.add(debugMarker(markerType, "Allocating arrays for locals"));
        
        // Why are we using size > 0 vs checking to see if var != null?
        //
        // REMEMBER THAT the analyzer will determine the variable slots to create for storage array based on its scan of EVERY
        // continuation/suspend point in the method. Imagine the method that we're instrumenting is this...
        //
        // public void example(Continuation c, String arg1) {
        //     String var1 = "hi";
        //     c.suspend();     
        //
        //     System.out.println(var1);
        //     int var2 = 5;
        //     c.suspend();
        //
        //     System.out.println(var1 + var2);
        // }
        //
        // There are two continuation/suspend points. The analyzer determines that method will need to assign variable slots for
        // localsObjectsVar+localsIntsVar. All the other locals vars will be null.
        //
        // If we ended up using var != null instead of size > 0, things would mess up on the first suspend(). The only variable initialized
        // at the first suspend is var1. As such, LocalStateGenerator ONLY CREATES AN ARRAY FOR localsObjectsVar. It doesn't touch
        // localsIntsVar because, at the first suspend(), var2 is UNINITALIZED. Nothing has been set to that variable slot.
        //
        //
        // The same thing applies to the operand stack. It doesn't make sense to create arrays for operand stack types that don't exist yet
        // at a continuation point, even though they may exist at other continuation points furhter down
        
        if (storageSizes.getIntsSize() > 0) {
            ret.add(debugMarker(markerType, "Allocating locals int array (" + storageSizes.getIntsSize() +  ")"));
            ret.add(allocateIntArray(settings, contVar, intsVar, storageSizes.getIntsSize()));
        } else {
            ret.add(debugMarker(markerType, "Skipping locals int array allocation because size is 0 (nothing will be stored in here)"));
        }
        
        if (storageSizes.getFloatsSize() > 0) {
            ret.add(debugMarker(markerType, "Allocating locals float array (" + storageSizes.getFloatsSize() +  ")"));
            ret.add(allocateFloatArray(settings, contVar, floatsVar, storageSizes.getFloatsSize()));
        } else {
            ret.add(debugMarker(markerType, "Skipping locals float array allocation because size is 0 (nothing will be stored in here)"));
        }
        
        if (storageSizes.getLongsSize() > 0) {
            ret.add(debugMarker(markerType, "Allocating locals long array (" + storageSizes.getLongsSize() +  ")"));
            ret.add(allocateLongArray(settings, contVar, longsVar, storageSizes.getLongsSize()));
        } else {
            ret.add(debugMarker(markerType, "Skipping locals long array allocation because size is 0 (nothing will be stored in here)"));
        }
        
        if (storageSizes.getDoublesSize() > 0) {
            ret.add(debugMarker(markerType, "Allocating locals double array (" + storageSizes.getDoublesSize() +  ")"));
            ret.add(allocateDoubleArray(settings, contVar, doublesVar, storageSizes.getDoublesSize()));
        } else {
            ret.add(debugMarker(markerType, "Skipping locals double array allocation because size is 0 (nothing will be stored in here)"));
        }
        
        if (storageSizes.getObjectsSize() > 0) {
            ret.add(debugMarker(markerType, "Allocating locals Object array (" + storageSizes.getObjectsSize() +  ")"));
            ret.add(allocateObjectArray(settings, contVar, objectsVar, storageSizes.getObjectsSize()));
        } else {
            ret.add(debugMarker(markerType, "Skipping locals Object array allocation because size is 0 (nothing will be stored in here)"));
        }
        
        return ret;
    }
    
    /**
     * Generates instructions to free the storage arrays required for storing the local variables table.
     * @param settings instrumenter settings
     * @param contVar variable containing the continuation object
     * @param storageVars variables to store locals in to
     * @param frame execution frame at the instruction where the local variables table is to be saved
     * @return instructions to free the locals storage arrays
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList freeLocalsStorageArrays(InstrumentationSettings settings, Variable contVar, StorageVariables storageVars,
            Frame<BasicValue> frame) {
        Validate.notNull(settings);
        Validate.notNull(contVar);
        Validate.notNull(storageVars);
        Validate.notNull(frame);
        MarkerType markerType = settings.getMarkerType();
        
        Variable intsVar = storageVars.getIntStorageVar();
        Variable floatsVar = storageVars.getFloatStorageVar();
        Variable longsVar = storageVars.getLongStorageVar();
        Variable doublesVar = storageVars.getDoubleStorageVar();
        Variable objectsVar = storageVars.getObjectStorageVar();
        
        StorageSizes storageSizes = computeSizes(frame);
        
        InsnList ret = new InsnList();
        ret.add(debugMarker(markerType, "Freeing arrays for locals"));
        
        if (storageSizes.getIntsSize() > 0) {
            ret.add(debugMarker(markerType, "Freeing locals int array"));
            ret.add(freeIntArray(settings, contVar, intsVar));
        } else {
            ret.add(debugMarker(markerType, "Skipping locals int array free because size is 0 (never created)"));
        }
        
        if (storageSizes.getFloatsSize() > 0) {
            ret.add(debugMarker(markerType, "Freeing locals float array"));
            ret.add(freeFloatArray(settings, contVar, floatsVar));
        } else {
            ret.add(debugMarker(markerType, "Skipping locals float array free because size is 0 (never created)"));
        }
        
        if (storageSizes.getLongsSize() > 0) {
            ret.add(debugMarker(markerType, "Freeing locals long array"));
            ret.add(freeLongArray(settings, contVar, longsVar));
        } else {
            ret.add(debugMarker(markerType, "Skipping locals long array free because size is 0 (never created)"));
        }
        
        if (storageSizes.getDoublesSize() > 0) {
            ret.add(debugMarker(markerType, "Freeing locals double array"));
            ret.add(freeDoubleArray(settings, contVar, doublesVar));
        } else {
            ret.add(debugMarker(markerType, "Skipping locals double array free because size is 0 (never created)"));
        }
        
        if (storageSizes.getObjectsSize() > 0) {
            ret.add(debugMarker(markerType, "Freeing locals Object array"));
            ret.add(freeObjectArray(settings, contVar, objectsVar));
        } else {
            ret.add(debugMarker(markerType, "Skipping locals Object array free because size is 0 (never created)"));
        }
        
        return ret;
    }

    /**
     * Compute sizes required for the storage arrays that will contain the local variables table at this frame.
     * @param frame frame to compute for
     * @return size required by each storage array
     * @throws NullPointerException if any argument is {@code null}
     */
    public static StorageSizes computeSizes(Frame<BasicValue> frame) {
        Validate.notNull(frame);

        // Count size required for each storage array
        int intsSize = 0;
        int longsSize = 0;
        int floatsSize = 0;
        int doublesSize = 0;
        int objectsSize = 0;
        for (int i = 0; i < frame.getLocals(); i++) {
            BasicValue basicValue = frame.getLocal(i);
            Type type = basicValue.getType();
            
            // If type == null, basicValue is pointing to uninitialized var -- basicValue.toString() will return '.'. This means that this
            // slot contains nothing to save. So, skip this slot if we encounter it.
            if (type == null) {
                continue;
            }
            
            // If type is 'Lnull;', this means that the slot has been assigned null and that "there has been no merge yet that would 'raise'
            // the type toward some class or interface type" (from ASM mailing list). We know this slot will always contain null at this
            // point in the code so we can avoid saving it. When we load it back up, we can simply push a null in to that slot, thereby
            // keeping the same 'Lnull;' type.
            if ("Lnull;".equals(type.getDescriptor())) {
                continue;
            }
            
            switch (type.getSort()) {
                case Type.BOOLEAN:
                case Type.BYTE:
                case Type.SHORT:
                case Type.CHAR:
                case Type.INT:
                    intsSize++;
                    break;
                case Type.FLOAT:
                    floatsSize++;
                    break;
                case Type.LONG:
                    longsSize++;
                    break;
                case Type.DOUBLE:
                    doublesSize++;
                    break;
                case Type.ARRAY:
                case Type.OBJECT:
                    objectsSize++;
                    break;
                case Type.METHOD:
                case Type.VOID:
                default:
                    throw new IllegalStateException();
            }
        }
        
        return new StorageSizes(intsSize, longsSize, floatsSize, doublesSize, objectsSize);
    }
}
