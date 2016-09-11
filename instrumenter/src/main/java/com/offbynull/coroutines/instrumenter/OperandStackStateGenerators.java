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
 * Utility class to generate bytecode instructions that save/load the operand stack.
 * @author Kasra Faghihi
 */
final class OperandStackStateGenerators {
    private OperandStackStateGenerators() {
        // do nothing
    }

    

    // A PRIMER ON HOW THIS CLASS WORKS...
    //
    // Thanks to the Frame object, we know how large the operand stack is and what type each position on the stack has. Imagine the
    // following operand stack.
    //
    // int3        <--- BOTTOM OF STACK
    // object2
    // int2
    // int1
    // object1     <--- TOP OF STACK
    //
    // 
    // TO SAVE...
    // ----------
    // We count the types on the stack and use that to determine the size of each storage array.
    //
    // int[]    = [0, 0, 0] (3 items)
    // float[]  = ignored (0 items)
    // long[]   = ignored (0 items)
    // double[] = ignored (0 items)
    // Object[] = [null, null] (2 items)
    //
    // Once we do that, we begin popping each item off the stack and storing it in to the appropiate storage array. Note that we start
    // at the end of each storage array and work our way down to index 0.
    //
    // AFTER POPPING object1 ... Object[] = [null,    object1]     int[] = [0,    0,    0]
    // AFTER POPPING int1    ... Object[] = [null,    object1]     int[] = [0,    0,    int1]
    // AFTER POPPING int2    ... Object[] = [null,    object1]     int[] = [0,    int2, int1]
    // AFTER POPPING object2 ... Object[] = [object2, object1]     int[] = [0,    int2, int1]
    // AFTER POPPING int3    ... Object[] = [object2, object1]     int[] = [int3, int2, int1]
    // 
    //
    // TO LOAD...
    // ----------
    // When we want to load back up what we saved, the process is essentially the reverse of saving. We use the Frame object again to
    // determine what types are being loaded back in, and we start reading items off the appropriate storage array and pushing them back on
    // to the stack.
    
    
    
    
    
    /**
     * Generates instructions to load the entire operand stack. Equivalent to calling
     * {@code loadOperandStack(markerType, storageVars, frame, 0, 0, frame.getStackSize())}.
     * @param markerType debug marker type
     * @param storageVars variables to load operand stack from
     * @param frame execution frame at the instruction where the operand stack is to be loaded
     * @return instructions to load the operand stack from the storage variables
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList loadOperandStack(MarkerType markerType, StorageVariables storageVars, Frame<BasicValue> frame) {
        return loadOperandStack(markerType, storageVars, frame, 0, 0, frame.getStackSize());
    }

    /**
     * Generates instructions to load a certain number of items to the top of the operand stack.
     * @param markerType debug marker type
     * @param storageVars variables to load operand stack from
     * @param frame execution frame at the instruction where the operand stack is to be loaded
     * @param storageStackStartIdx stack position where {@code storageVars} starts from
     * @param storageStackLoadIdx stack position where loading should start from
     * @param count number of stack items to load
     * @return instructions to load the operand stack from the specified storage variables
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if any numeric argument is negative, or if you're trying to load stack items that aren't available
     * in the storage vars (stack items before {@code storageStackStartIdx}), or if you're trying to load too many items on the stack (such
     * that it goes past {@code frame.getStackSize()})
     */
    public static InsnList loadOperandStack(MarkerType markerType, StorageVariables storageVars, Frame<BasicValue> frame,
            int storageStackStartIdx,  // stack idx which the storage was started at
            int storageStackLoadIdx,   // stack idx we should start loading at
            int count) {
        Validate.notNull(markerType);
        Validate.notNull(storageVars);
        Validate.notNull(frame);
        // no negs allowed
        Validate.isTrue(storageStackStartIdx >= 0);
        Validate.isTrue(storageStackLoadIdx >= 0);
        Validate.isTrue(count >= 0);
        Validate.isTrue(storageStackLoadIdx >= storageStackStartIdx);
        Validate.isTrue(storageStackStartIdx + count <= frame.getStackSize());
        Validate.isTrue(storageStackStartIdx + count >= 0); // likely will never overflow unless crazy high count passedin, but just in case
        
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
        
        // Increment offsets for parts of the storage arrays we don't care about. We need to do this so when we load we're loading from the
        // correct offsets in the storage arrays
        for (int i = storageStackStartIdx; i < storageStackLoadIdx; i++) {
            BasicValue basicValue = frame.getStack(i);
            Type type = basicValue.getType();
            
            // If type is 'Lnull;', this means that the slot has been assigned null and that "there has been no merge yet that would 'raise'
            // the type toward some class or interface type" (from ASM mailing list). We know this slot will always contain null at this
            // point in the code so there's no specific value to load up from the array.
            if (type.getSort() == Type.OBJECT && "Lnull;".equals(type.getDescriptor())) {
                continue; // skip
            }
            
            switch (type.getSort()) {
                case Type.BOOLEAN:
                case Type.BYTE:
                case Type.SHORT:
                case Type.CHAR:
                case Type.INT:
                    intsCounter++;
                    break;
                case Type.FLOAT:
                    floatsCounter++;
                    break;
                case Type.LONG:
                    longsCounter++;
                    break;
                case Type.DOUBLE:
                    doublesCounter++;
                    break;
                case Type.ARRAY:
                case Type.OBJECT:
                    objectsCounter++;
                    break;
                case Type.METHOD:
                case Type.VOID:
                default:
                    throw new IllegalArgumentException();
            }
        }
        
        // Restore the stack
        ret.add(debugMarker(markerType, "Loading stack items"));
        for (int i = storageStackLoadIdx; i < storageStackLoadIdx + count; i++) {
            BasicValue basicValue = frame.getStack(i);
            Type type = basicValue.getType();
            
            // If type is 'Lnull;', this means that the slot has been assigned null and that "there has been no merge yet that would 'raise'
            // the type toward some class or interface type" (from ASM mailing list). We know this slot will always contain null at this
            // point in the code so there's no specific value to load up from the array. Instead we push a null in to that slot, thereby
            // keeping the same 'Lnull;' type originally assigned to that slot (it doesn't make sense to do a CHECKCAST because 'null' is
            // not a real class and can never be a real class -- null is a reserved word in Java).
            if (type.getSort() == Type.OBJECT && "Lnull;".equals(type.getDescriptor())) {
                ret.add(debugMarker(markerType, "Loading null value at " + i));
                ret.add(new InsnNode(Opcodes.ACONST_NULL));
                continue;
            }

            // Load item from stack storage array
            ret.add(debugMarker(markerType, "Loading from container at" + i));

            // Convert the item to an object (if not already an object) and stores it in local vars table. Item removed from stack.
            switch (type.getSort()) {
                case Type.BOOLEAN:
                case Type.BYTE:
                case Type.SHORT:
                case Type.CHAR:
                case Type.INT:
                    ret.add(debugMarker(markerType, "Loading int at " + i + " from storage index " + intsCounter));
                    ret.add(new VarInsnNode(Opcodes.ALOAD, intsVar.getIndex())); // [int[]]
                    ret.add(new LdcInsnNode(intsCounter));                       // [int[], idx]
                    ret.add(new InsnNode(Opcodes.IALOAD));                       // [val]
                    intsCounter++;
                    break;
                case Type.FLOAT:
                    ret.add(debugMarker(markerType, "Loading float at " + i + " from storage index " + floatsCounter));
                    ret.add(new VarInsnNode(Opcodes.ALOAD, floatsVar.getIndex())); // [float[]]
                    ret.add(new LdcInsnNode(floatsCounter));                       // [float[], idx]
                    ret.add(new InsnNode(Opcodes.FALOAD));                         // [val]
                    floatsCounter++;
                    break;
                case Type.LONG:
                    ret.add(debugMarker(markerType, "Loading long at " + i + " from storage index " + longsCounter));
                    ret.add(new VarInsnNode(Opcodes.ALOAD, longsVar.getIndex()));  // [long[]]
                    ret.add(new LdcInsnNode(longsCounter));                        // [long[], idx]
                    ret.add(new InsnNode(Opcodes.LALOAD));                         // [val_PART1, val_PART2]
                    longsCounter++;
                    break;
                case Type.DOUBLE:
                    ret.add(debugMarker(markerType, "Loading double at " + i + " from storage index " + doublesCounter));
                    ret.add(new VarInsnNode(Opcodes.ALOAD, doublesVar.getIndex()));  // [double[]]
                    ret.add(new LdcInsnNode(doublesCounter));                        // [double[], idx]
                    ret.add(new InsnNode(Opcodes.DALOAD));                           // [val_PART1, val_PART2]
                    doublesCounter++;
                    break;
                case Type.ARRAY:
                case Type.OBJECT:
                    ret.add(debugMarker(markerType, "Loading object at " + i + " from storage index " + objectsCounter));
                    ret.add(new VarInsnNode(Opcodes.ALOAD, objectsVar.getIndex())); // [Object[]]
                    ret.add(new LdcInsnNode(objectsCounter));                       // [Object[], idx]
                    ret.add(new InsnNode(Opcodes.AALOAD));                          // [val]
                    ret.add(new TypeInsnNode(Opcodes.CHECKCAST, basicValue.getType().getInternalName()));
                    objectsCounter++;
                    break;
                case Type.METHOD:
                case Type.VOID:
                default:
                    throw new IllegalArgumentException();
            }
        }

        return ret;
    }


    /**
     * Generates instructions to save the entire operand stack. Equivalent to calling
     * {@code saveOperandStack(markerType, storageVars, frame, frame.getStackSize())}.
     * <p>
     * The instructions generated here expect the operand stack to be fully loaded at this point.
     * @param markerType debug marker type
     * @param storageVars variables to store operand stack in to
     * @param frame execution frame at the instruction where the operand stack is to be saved
     * @return instructions to save the operand stack to the storage variables
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList saveOperandStack(MarkerType markerType, StorageVariables storageVars, Frame<BasicValue> frame) {
        return saveOperandStack(markerType, storageVars, frame, frame.getStackSize());
    }

    /**
     * Generates instructions to save a certain number of items from the top of the operand stack.
     * <p>
     * The instructions generated here expect the operand stack to be fully loaded at this point.
     * @param markerType debug marker type
     * @param storageVars variables to store operand stack in to
     * @param frame execution frame at the instruction where the operand stack is to be saved
     * @param count number of items to store from the stack
     * @return instructions to save the operand stack to the storage variables
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code size} is larger than the number of items in the stack at {@code frame} (or is negative),
     * or if {@code count} is larger than {@code top} (or is negative)
     */
    public static InsnList saveOperandStack(MarkerType markerType, StorageVariables storageVars, Frame<BasicValue> frame, int count) {
        Validate.notNull(markerType);
        Validate.notNull(storageVars);
        Validate.notNull(frame);
        Validate.isTrue(count >= 0);
        Validate.isTrue(count <= frame.getStackSize());


        Variable intsVar = storageVars.getIntStorageVar();
        Variable floatsVar = storageVars.getFloatStorageVar();
        Variable longsVar = storageVars.getLongStorageVar();
        Variable doublesVar = storageVars.getDoubleStorageVar();
        Variable objectsVar = storageVars.getObjectStorageVar();

        StorageSizes storageSizes = computeSizes(frame, frame.getStackSize() - count, count);

        int intsCounter = storageSizes.getIntsSize() - 1;
        int floatsCounter = storageSizes.getFloatsSize() - 1;
        int longsCounter = storageSizes.getLongsSize() - 1;
        int doublesCounter = storageSizes.getDoublesSize() - 1;
        int objectsCounter = storageSizes.getObjectsSize() - 1;


        InsnList ret = new InsnList();
                
        // Create stack storage arrays and save them
        ret.add(merge(
                debugMarker(markerType, "Saving operand stack (" + count + " items)"),
                mergeIf(storageSizes.getIntsSize() > 0, () -> new Object[] {
                    debugMarker(markerType, "Generating ints container (" + storageSizes.getIntsSize() + ")"),
                    new LdcInsnNode(storageSizes.getIntsSize()),
                    new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_INT),
                    new VarInsnNode(Opcodes.ASTORE, intsVar.getIndex())
                }),
                mergeIf(storageSizes.getFloatsSize() > 0, () -> new Object[] {
                    debugMarker(markerType, "Generating floats container (" + storageSizes.getFloatsSize() + ")"),
                    new LdcInsnNode(storageSizes.getFloatsSize()),
                    new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_FLOAT),
                    new VarInsnNode(Opcodes.ASTORE, floatsVar.getIndex())
                }),
                mergeIf(storageSizes.getLongsSize() > 0, () -> new Object[] {
                    debugMarker(markerType, "Generating longs container (" + storageSizes.getLongsSize() + ")"),
                    new LdcInsnNode(storageSizes.getLongsSize()),
                    new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_LONG),
                    new VarInsnNode(Opcodes.ASTORE, longsVar.getIndex())
                }),
                mergeIf(storageSizes.getDoublesSize() > 0, () -> new Object[] {
                    debugMarker(markerType, "Generating doubles container (" + storageSizes.getDoublesSize() + ")"),
                    new LdcInsnNode(storageSizes.getDoublesSize()),
                    new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_DOUBLE),
                    new VarInsnNode(Opcodes.ASTORE, doublesVar.getIndex())
                }),
                mergeIf(storageSizes.getObjectsSize() > 0, () -> new Object[] {
                    debugMarker(markerType, "Generating objects container (" + storageSizes.getObjectsSize() + ")"),
                    new LdcInsnNode(storageSizes.getObjectsSize()),
                    new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"),
                    new VarInsnNode(Opcodes.ASTORE, objectsVar.getIndex())
                })
        ));

        // Save the stack
        int start = frame.getStackSize() - 1;
        int end = frame.getStackSize() - count;
        for (int i = start; i >= end; i--) {
            BasicValue basicValue = frame.getStack(i);
            Type type = basicValue.getType();
            
            // If type is 'Lnull;', this means that the slot has been assigned null and that "there has been no merge yet that would 'raise'
            // the type toward some class or interface type" (from ASM mailing list). We know this slot will always contain null at this
            // point in the code so we can avoid saving it (but we still need to do a POP to get rid of it). When we load it back up, we can
            // simply push a null in to that slot, thereby keeping the same 'Lnull;' type.
            if ("Lnull;".equals(type.getDescriptor())) {
                ret.add(debugMarker(markerType, "Skipping null value at " + i));
                ret.add(new InsnNode(Opcodes.POP));
                continue;
            }

            // Convert the item to an object (if not already an object) and stores it in local vars table. Item removed from stack.
            switch (type.getSort()) {
                case Type.BOOLEAN:
                case Type.BYTE:
                case Type.SHORT:
                case Type.CHAR:
                case Type.INT:
                    ret.add(debugMarker(markerType, "Popping/storing int at " + i + " to storage index " + intsCounter));
                    ret.add(new VarInsnNode(Opcodes.ALOAD, intsVar.getIndex())); // [val, int[]]
                    ret.add(new InsnNode(Opcodes.SWAP));                         // [int[], val]
                    ret.add(new LdcInsnNode(intsCounter));                       // [int[], val, idx]
                    ret.add(new InsnNode(Opcodes.SWAP));                         // [int[], idx, val]
                    ret.add(new InsnNode(Opcodes.IASTORE));                      // []
                    intsCounter--;
                    break;
                case Type.FLOAT:
                    ret.add(debugMarker(markerType, "Popping/storing float at " + i + " to storage index " + floatsCounter));
                    ret.add(new VarInsnNode(Opcodes.ALOAD, floatsVar.getIndex())); // [val, float[]]
                    ret.add(new InsnNode(Opcodes.SWAP));                           // [float[], val]
                    ret.add(new LdcInsnNode(floatsCounter));                       // [float[], val, idx]
                    ret.add(new InsnNode(Opcodes.SWAP));                           // [float[], idx, val]
                    ret.add(new InsnNode(Opcodes.FASTORE));                        // []
                    floatsCounter--;
                    break;
                case Type.LONG:
                    ret.add(debugMarker(markerType, "Popping/storing long at " + i + " to storage index " + longsCounter));
                    ret.add(new VarInsnNode(Opcodes.ALOAD, longsVar.getIndex()));  // [val_PART1, val_PART2, long[]]
                    ret.add(new LdcInsnNode(longsCounter));                        // [val_PART1, val_PART2, long[], idx]
                    ret.add(new InsnNode(Opcodes.DUP2_X2));                        // [long[], idx, val_PART1, val_PART2, long[], idx]
                    ret.add(new InsnNode(Opcodes.POP2));                           // [long[], idx, val_PART1, val_PART2]
                    ret.add(new InsnNode(Opcodes.LASTORE));                        // []
                    longsCounter--;
                    break;
                case Type.DOUBLE:
                    ret.add(debugMarker(markerType, "Popping/storing double at " + i + " to storage index " + doublesCounter));
                    ret.add(new VarInsnNode(Opcodes.ALOAD, doublesVar.getIndex()));  // [val_PART1, val_PART2, double[]]
                    ret.add(new LdcInsnNode(doublesCounter));                        // [val_PART1, val_PART2, double[], idx]
                    ret.add(new InsnNode(Opcodes.DUP2_X2));                          // [double[], idx, val_PART1, val_PART2, double[], idx]
                    ret.add(new InsnNode(Opcodes.POP2));                             // [double[], idx, val_PART1, val_PART2]
                    ret.add(new InsnNode(Opcodes.DASTORE));                          // []
                    doublesCounter--;
                    break;
                case Type.ARRAY:
                case Type.OBJECT:
                    ret.add(debugMarker(markerType, "Popping/storing object at " + i + " to storage index " + objectsCounter));
                    ret.add(new VarInsnNode(Opcodes.ALOAD, objectsVar.getIndex())); // [val, object[]]
                    ret.add(new InsnNode(Opcodes.SWAP));                            // [object[], val]
                    ret.add(new LdcInsnNode(objectsCounter));                       // [object[], val, idx]
                    ret.add(new InsnNode(Opcodes.SWAP));                            // [object[], idx, val]
                    ret.add(new InsnNode(Opcodes.AASTORE));                         // []
                    objectsCounter--;
                    break;
                case Type.METHOD:
                case Type.VOID:
                default:
                    throw new IllegalArgumentException();
            }
        }

        // At this point, the storage array will contain the saved operand stack.
        
        // Restore the stack
        ret.add(debugMarker(markerType, "Reloading stack items"));
        InsnList reloadInsnList = loadOperandStack(markerType, storageVars, frame,
                frame.getStackSize() - count,
                frame.getStackSize() - count,
                count);
        ret.add(reloadInsnList);

        return ret;
    }
    

    /**
     * Compute sizes required for the storage arrays that will contain the operand stack at this frame.
     * @param frame frame to compute for
     * @param offset the position within the operand stack to start calculating
     * @param length the number of stack items to include in calculation
     * @return size required by each storage array
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if any numeric argument is negative, or if {@code offset + length} is larger than the size of the
     * operand stack
     */
    public static StorageSizes computeSizes(Frame<BasicValue> frame, int offset, int length) {
        Validate.notNull(frame);
        Validate.isTrue(offset >= 0);
        Validate.isTrue(length >= 0);
        Validate.isTrue(offset < frame.getStackSize());
        Validate.isTrue(offset + length <= frame.getStackSize());
        
        // Count size required for each storage array
        int intsSize = 0;
        int longsSize = 0;
        int floatsSize = 0;
        int doublesSize = 0;
        int objectsSize = 0;
        for (int i = offset + length - 1; i >= offset; i--) {
            BasicValue basicValue = frame.getStack(i);
            Type type = basicValue.getType();
            
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
