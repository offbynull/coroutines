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

import com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable;
import com.offbynull.coroutines.instrumenter.generators.DebugGenerators.MarkerType;
import static com.offbynull.coroutines.instrumenter.generators.DebugGenerators.debugMarker;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.merge;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.mergeIf;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
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
     * @param markerType debug marker type
     * @param storageVars variables to load locals from
     * @param frame execution frame at the instruction for which the local variables table is to be restored
     * @return instructions to load the local variables table from an array
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList loadLocals(MarkerType markerType, StorageVariables storageVars, Frame<BasicValue> frame) {
        Validate.notNull(markerType);
        Validate.notNull(storageVars);
        Validate.notNull(frame);

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
     * @param markerType debug marker type
     * @param storageVars variables to store locals in to
     * @param frame execution frame at the instruction where the local variables table is to be saved
     * @return instructions to save the local variables table in to an array
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList saveLocals(MarkerType markerType, StorageVariables storageVars, Frame<BasicValue> frame) {
        Validate.notNull(markerType);
        Validate.notNull(storageVars);
        Validate.notNull(frame);

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

        StorageSizes storageSizes = computeSizes(frame);
        

        InsnList ret = new InsnList();
                
        // Create storage arrays and save them in respective storage vars
        ret.add(merge(
                debugMarker(markerType, "Saving locals"),
                mergeIf(intsVar != null, () -> new Object[] {
                    debugMarker(markerType, "Generating ints container (" + storageSizes.getIntsSize() + ")"),
                    new LdcInsnNode(storageSizes.getIntsSize()),
                    new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_INT),
                    new VarInsnNode(Opcodes.ASTORE, intsVar.getIndex())
                }),
                mergeIf(floatsVar != null, () -> new Object[] {
                    debugMarker(markerType, "Generating floats container (" + storageSizes.getFloatsSize() + ")"),
                    new LdcInsnNode(storageSizes.getFloatsSize()),
                    new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_FLOAT),
                    new VarInsnNode(Opcodes.ASTORE, floatsVar.getIndex())
                }),
                mergeIf(longsVar != null, () -> new Object[] {
                    debugMarker(markerType, "Generating longs container (" + storageSizes.getLongsSize() + ")"),
                    new LdcInsnNode(storageSizes.getLongsSize()),
                    new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_LONG),
                    new VarInsnNode(Opcodes.ASTORE, longsVar.getIndex())
                }),
                mergeIf(doublesVar != null, () -> new Object[] {
                    debugMarker(markerType, "Generating doubles container (" + storageSizes.getDoublesSize() + ")"),
                    new LdcInsnNode(storageSizes.getDoublesSize()),
                    new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_DOUBLE),
                    new VarInsnNode(Opcodes.ASTORE, doublesVar.getIndex())
                }),
                mergeIf(objectsVar != null, () -> new Object[] {
                    debugMarker(markerType, "Generating objects container (" + storageSizes.getObjectsSize() + ")"),
                    new LdcInsnNode(storageSizes.getObjectsSize()),
                    new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"),
                    new VarInsnNode(Opcodes.ASTORE, objectsVar.getIndex())
                })
        ));

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
