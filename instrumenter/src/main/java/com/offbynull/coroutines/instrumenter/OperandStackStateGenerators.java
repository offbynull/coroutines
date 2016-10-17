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
import static com.offbynull.coroutines.instrumenter.LocalsStateGenerators.computeSizes;
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
     * {@code loadOperandStack(settings, storageVars, frame, 0, 0, frame.getStackSize())}.
     * @param settings instrumenter settings
     * @param storageVars variables to load operand stack from
     * @param frame execution frame at the instruction where the operand stack is to be loaded
     * @return instructions to load the operand stack from the storage variables
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList loadOperandStack(InstrumentationSettings settings, StorageVariables storageVars, Frame<BasicValue> frame) {
        return loadOperandStack(settings, storageVars, frame, 0, 0, frame.getStackSize());
    }

    /**
     * Generates instructions to load a certain number of items to the top of the operand stack.
     * @param settings instrumenter settings
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
    public static InsnList loadOperandStack(InstrumentationSettings settings, StorageVariables storageVars, Frame<BasicValue> frame,
            int storageStackStartIdx,  // stack idx which the storage was started at
            int storageStackLoadIdx,   // stack idx we should start loading at
            int count) {
        Validate.notNull(settings);
        Validate.notNull(storageVars);
        Validate.notNull(frame);
        // no negs allowed
        Validate.isTrue(storageStackStartIdx >= 0);
        Validate.isTrue(storageStackLoadIdx >= 0);
        Validate.isTrue(count >= 0);
        Validate.isTrue(storageStackLoadIdx >= storageStackStartIdx);
        Validate.isTrue(storageStackStartIdx + count <= frame.getStackSize());
        Validate.isTrue(storageStackStartIdx + count >= 0); // likely will never overflow unless crazy high count passedin, but just in case
        
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
     * {@code saveOperandStack(settings, storageVars, frame, frame.getStackSize())}.
     * <p>
     * The instructions generated here expect the operand stack to be fully loaded. The stack items specified by {@code frame} must actually
     * all be on the operand stack.
     * <p>
     * REMEMBER: The items aren't returned to the operand stack after they've been saved (they have been popped off the stack). If you want
     * them back on the operand stack, reload using {@code loadOperandStack(setings, storageVars, frame)}.
     * @param settings instrumenter settings
     * @param storageVars variables to store operand stack in to
     * @param frame execution frame at the instruction where the operand stack is to be saved
     * @return instructions to save the operand stack to the storage variables
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList saveOperandStack(InstrumentationSettings settings, StorageVariables storageVars, Frame<BasicValue> frame) {
        return saveOperandStack(settings, storageVars, frame, frame.getStackSize());
    }

    /**
     * Generates instructions to save a certain number of items from the top of the operand stack.
     * <p>
     * The instructions generated here expect the operand stack to be fully loaded. The stack items specified by {@code frame} must actually
     * all be on the operand stack.
     * <p>
     * REMEMBER: The items aren't returned to the operand stack after they've been saved (they have been popped off the stack). If you want
     * them back on the operand stack, reload using
     * {@code loadOperandStack(settings, storageVars, frame, frame.getStackSize() - count, frame.getStackSize() - count, count)}.
     * @param settings instrumenter settings
     * @param storageVars variables to store operand stack in to
     * @param frame execution frame at the instruction where the operand stack is to be saved
     * @param count number of items to store from the stack
     * @return instructions to save the operand stack to the storage variables
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code count} is larger than {@code frame.getStackSize()}
     */
    public static InsnList saveOperandStack(InstrumentationSettings settings, StorageVariables storageVars, Frame<BasicValue> frame,
            int count) {
        Validate.notNull(settings);
        Validate.notNull(storageVars);
        Validate.notNull(frame);
        Validate.isTrue(count >= 0);
        Validate.isTrue(count <= frame.getStackSize());

        MarkerType markerType = settings.getMarkerType();

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

        ret.add(debugMarker(markerType, "Saving operand stack (" + count + " items)"));

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

        // At this point, the storage array will contain the saved operand stack AND THE STACK WILL HAVE count ITEMS POPPED OFF OF IT.
        // 
        // Reload using...
        // ---------------
        // ret.add(debugMarker(settings, "Reloading stack items"));
        // InsnList reloadInsnList = loadOperandStack(settings, storageVars, frame,
        //         frame.getStackSize() - count,
        //         frame.getStackSize() - count,
        //         count);
        // ret.add(reloadInsnList);

        return ret;
    }
    
    /**
     * Generates instructions to create the storage arrays required for storing the entire operand stack. Equivalent to calling
     * {@code allocateOperandStackStorageArrays(settings, storageVars, frame, frame.getStackSize())}.
     * @param settings instrumenter settings
     * @param contVar variable containing the continuation object
     * @param storageVars variables to store operand stack in to
     * @param frame execution frame at the instruction where the operand stack is to be saved
     * @return instructions to allocate the operand stack storage arrays
     * @throws IllegalArgumentException if {@code count} is larger than {@code frame.getStackSize()}
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList allocateOperandStackStorageArrays(InstrumentationSettings settings, Variable contVar,
            StorageVariables storageVars, Frame<BasicValue> frame) {
        return allocateOperandStackStorageArrays(settings, contVar, storageVars, frame, frame.getStackSize());
    }
    
    /**
     * Generates instructions to create the storage arrays required for storing a certain number of items from the top of the operand stack.
     * @param settings instrumenter settings
     * @param contVar variable containing the continuation object
     * @param storageVars variables to store operand stack in to
     * @param frame execution frame at the instruction where the operand stack is to be saved
     * @param count number of items to store from the stack
     * @return instructions to allocate the operand stack storage arrays
     * @throws IllegalArgumentException if {@code count} is larger than {@code frame.getStackSize()}
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList allocateOperandStackStorageArrays(InstrumentationSettings settings, Variable contVar,
            StorageVariables storageVars, Frame<BasicValue> frame, int count) {
        Validate.notNull(settings);
        Validate.notNull(contVar);
        Validate.notNull(storageVars);
        Validate.notNull(frame);
        Validate.isTrue(count >= 0);
        Validate.isTrue(count <= frame.getStackSize());
        
        MarkerType markerType = settings.getMarkerType();
        
        Variable intsVar = storageVars.getIntStorageVar();
        Variable floatsVar = storageVars.getFloatStorageVar();
        Variable longsVar = storageVars.getLongStorageVar();
        Variable doublesVar = storageVars.getDoubleStorageVar();
        Variable objectsVar = storageVars.getObjectStorageVar();

        StorageSizes storageSizes = computeSizes(frame, frame.getStackSize() - count, count);
        
        InsnList ret = new InsnList();
        ret.add(debugMarker(markerType, "Allocating arrays for operand stack"));
        
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
            ret.add(debugMarker(markerType, "Allocating operand stack int array (" + storageSizes.getIntsSize() +  ")"));
            ret.add(allocateIntArray(settings, contVar, intsVar, storageSizes.getIntsSize()));
        } else {
            ret.add(debugMarker(markerType, "Skipping operand stack int array allocation because size is 0"
                    + " (nothing will be stored in here)"));
        }
        
        if (storageSizes.getFloatsSize() > 0) {
            ret.add(debugMarker(markerType, "Allocating operand stack float array (" + storageSizes.getFloatsSize() +  ")"));
            ret.add(allocateFloatArray(settings, contVar, floatsVar, storageSizes.getFloatsSize()));
        } else {
            ret.add(debugMarker(markerType, "Skipping operand stack float array allocation because size is 0"
                    + " (nothing will be stored in here)"));
        }
        
        if (storageSizes.getLongsSize() > 0) {
            ret.add(debugMarker(markerType, "Allocating operand stack long array (" + storageSizes.getLongsSize() +  ")"));
            ret.add(allocateLongArray(settings, contVar, longsVar, storageSizes.getLongsSize()));
        } else {
            ret.add(debugMarker(markerType, "Skipping operand stack long array allocation because size is 0"
                    + " (nothing will be stored in here)"));
        }
        
        if (storageSizes.getDoublesSize() > 0) {
            ret.add(debugMarker(markerType, "Allocating operand stack double array (" + storageSizes.getDoublesSize() +  ")"));
            ret.add(allocateDoubleArray(settings, contVar, doublesVar, storageSizes.getDoublesSize()));
        } else {
            ret.add(debugMarker(markerType, "Skipping operand stack double array allocation because size is 0"
                    + " (nothing will be stored in here)"));
        }
        
        if (storageSizes.getObjectsSize() > 0) {
            ret.add(debugMarker(markerType, "Allocating operand stack Object array (" + storageSizes.getObjectsSize() +  ")"));
            ret.add(allocateObjectArray(settings, contVar, objectsVar, storageSizes.getObjectsSize()));
        } else {
            ret.add(debugMarker(markerType, "Skipping operand stack Object array allocation because size is 0"
                    + " (nothing will be stored in here)"));
        }
        
        return ret;
    }
    
    /**
     * Generates instructions to free the storage arrays required for storing the operand stack.
     * @param settings instrumenter settings
     * @param contVar variable containing the continuation object
     * @param storageVars variables to store operand stack in to
     * @param frame execution frame at the instruction where the operand stack is to be saved
     * @param count number of stack items to that the storage arrays were generated for
     * @return instructions to allocate the operand stack storage arrays
     * @throws IllegalArgumentException if {@code count} is larger than {@code frame.getStackSize()}
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList freeOperandStackStorageArrays(InstrumentationSettings settings, Variable contVar, StorageVariables storageVars,
            Frame<BasicValue> frame) {
        return freeOperandStackStorageArrays(settings, contVar, storageVars, frame, frame.getStackSize());
    }
    
    /**
     * Generates instructions to free the storage arrays required for storing some predetermined number of items from the top of the operand
     * stack.
     * @param settings instrumenter settings
     * @param contVar variable containing the continuation object
     * @param storageVars variables to store operand stack in to
     * @param frame execution frame at the instruction where the operand stack is to be saved
     * @param count number of stack items to that the storage arrays were generated for
     * @return instructions to allocate the operand stack storage arrays
     * @throws IllegalArgumentException if {@code count} is larger than {@code frame.getStackSize()}
     * @throws NullPointerException if any argument is {@code null}
     */
    public static InsnList freeOperandStackStorageArrays(InstrumentationSettings settings, Variable contVar, StorageVariables storageVars,
            Frame<BasicValue> frame, int count) {
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
        
        StorageSizes storageSizes = computeSizes(frame, frame.getStackSize() - count, count);
        
        InsnList ret = new InsnList();
        ret.add(debugMarker(markerType, "Freeing arrays for operand stack"));
        
        if (storageSizes.getIntsSize() > 0) {
            ret.add(debugMarker(markerType, "Freeing operand stack int array"));
            ret.add(freeIntArray(settings, contVar, intsVar));
        } else {
            ret.add(debugMarker(markerType, "Skipping operand stack int array free because size is 0 (never created)"));
        }
        
        if (storageSizes.getFloatsSize() > 0) {
            ret.add(debugMarker(markerType, "Freeing operand stack float array"));
            ret.add(freeFloatArray(settings, contVar, floatsVar));
        } else {
            ret.add(debugMarker(markerType, "Skipping operand stack float array free because size is 0 (never created)"));
        }
        
        if (storageSizes.getLongsSize() > 0) {
            ret.add(debugMarker(markerType, "Freeing operand stack long array"));
            ret.add(freeLongArray(settings, contVar, longsVar));
        } else {
            ret.add(debugMarker(markerType, "Skipping operand stack long array free because size is 0 (never created)"));
        }
        
        if (storageSizes.getDoublesSize() > 0) {
            ret.add(debugMarker(markerType, "Freeing operand stack double array"));
            ret.add(freeDoubleArray(settings, contVar, doublesVar));
        } else {
            ret.add(debugMarker(markerType, "Skipping operand stack double array free because size is 0 (never created)"));
        }
        
        if (storageSizes.getObjectsSize() > 0) {
            ret.add(debugMarker(markerType, "Freeing operand stack Object array"));
            ret.add(freeObjectArray(settings, contVar, objectsVar));
        } else {
            ret.add(debugMarker(markerType, "Skipping operand stack Object array free because size is 0 (never created)"));
        }
        
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
