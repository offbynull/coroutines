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
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.call;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.invoke;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.loadVar;
import static com.offbynull.coroutines.instrumenter.generators.GenericGenerators.merge;
import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.FrameAllocator;
import java.lang.reflect.Method;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

final class AllocationGenerators {

    static final Method CONTINUATION_GETALLOCATOR_METHOD
            = MethodUtils.getAccessibleMethod(Continuation.class, "getAllocator");
    
    static final Method FRAMEALLOCATOR_ALLOCATEINT_METHOD
            = MethodUtils.getAccessibleMethod(FrameAllocator.class, "allocateIntArray", Integer.TYPE);
    static final Method FRAMEALLOCATOR_ALLOCATELONG_METHOD
            = MethodUtils.getAccessibleMethod(FrameAllocator.class, "allocateLongArray", Integer.TYPE);
    static final Method FRAMEALLOCATOR_ALLOCATEFLOAT_METHOD
            = MethodUtils.getAccessibleMethod(FrameAllocator.class, "allocateFloatArray", Integer.TYPE);
    static final Method FRAMEALLOCATOR_ALLOCATEDOUBLE_METHOD
            = MethodUtils.getAccessibleMethod(FrameAllocator.class, "allocateDoubleArray", Integer.TYPE);
    static final Method FRAMEALLOCATOR_ALLOCATEOBJECT_METHOD
            = MethodUtils.getAccessibleMethod(FrameAllocator.class, "allocateObjectArray", Integer.TYPE);
    static final Method FRAMEALLOCATOR_RELEASEINT_METHOD
            = MethodUtils.getAccessibleMethod(FrameAllocator.class, "releaseIntArray", int[].class);
    static final Method FRAMEALLOCATOR_RELEASELONG_METHOD
            = MethodUtils.getAccessibleMethod(FrameAllocator.class, "releaseLongArray", long[].class);
    static final Method FRAMEALLOCATOR_RELEASEFLOAT_METHOD
            = MethodUtils.getAccessibleMethod(FrameAllocator.class, "releaseFloatArray", float[].class);
    static final Method FRAMEALLOCATOR_RELEASEDOUBLE_METHOD
            = MethodUtils.getAccessibleMethod(FrameAllocator.class, "releaseDoubleArray", double[].class);
    static final Method FRAMEALLOCATOR_RELEASEOBJECT_METHOD
            = MethodUtils.getAccessibleMethod(FrameAllocator.class, "releaseObjectArray", Object[].class);

    private AllocationGenerators() {
        // do nothing
    }

    public static InsnList allocateIntArray(
            InstrumentationSettings settings,
            Variable contVar,
            Variable arrayVar,
            int size) {
        Validate.notNull(settings);
        Validate.notNull(contVar);
        Validate.notNull(arrayVar);
        Validate.isTrue(size > 0);

        MarkerType markerType = settings.getMarkerType();
        boolean useAllocator = settings.isCustomFrameAllocator();

        InsnList ret;

        if (!useAllocator) {
            ret = merge(
                    debugMarker(markerType, "Generating int array via new (" + size + ")"),
                    new LdcInsnNode(size),
                    new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_INT),
                    new VarInsnNode(Opcodes.ASTORE, arrayVar.getIndex())
            );
        } else {
            ret = merge(
                    debugMarker(markerType, "Generating int array via allocator (" + size + ")"),
                    call(CONTINUATION_GETALLOCATOR_METHOD, loadVar(contVar)),    // [alloc]
                    new LdcInsnNode(size),                                       // [alloc, size]
                    invoke(FRAMEALLOCATOR_ALLOCATEINT_METHOD),                   // [array]
                    new VarInsnNode(Opcodes.ASTORE, arrayVar.getIndex())         // []
            );
        }
        
        return ret;
    }

    public static InsnList allocateFloatArray(
            InstrumentationSettings settings,
            Variable contVar,
            Variable arrayVar,
            int size) {
        Validate.notNull(settings);
        Validate.notNull(contVar);
        Validate.notNull(arrayVar);
        Validate.isTrue(size > 0);

        MarkerType markerType = settings.getMarkerType();
        boolean useAllocator = settings.isCustomFrameAllocator();

        InsnList ret;

        if (!useAllocator) {
            ret = merge(
                    debugMarker(markerType, "Generating float array via new (" + size + ")"),
                    new LdcInsnNode(size),
                    new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_FLOAT),
                    new VarInsnNode(Opcodes.ASTORE, arrayVar.getIndex())
            );
        } else {
            ret = merge(
                    debugMarker(markerType, "Generating float array via allocator (" + size + ")"),
                    call(CONTINUATION_GETALLOCATOR_METHOD, loadVar(contVar)),    // [alloc]
                    new LdcInsnNode(size),                                       // [alloc, size]
                    invoke(FRAMEALLOCATOR_ALLOCATEFLOAT_METHOD),                 // [array]
                    new VarInsnNode(Opcodes.ASTORE, arrayVar.getIndex())         // []
            );
        }
        
        return ret;
    }

    public static InsnList allocateLongArray(
            InstrumentationSettings settings,
            Variable contVar,
            Variable arrayVar,
            int size) {
        Validate.notNull(settings);
        Validate.notNull(contVar);
        Validate.notNull(arrayVar);
        Validate.isTrue(size > 0);

        MarkerType markerType = settings.getMarkerType();
        boolean useAllocator = settings.isCustomFrameAllocator();

        InsnList ret;

        if (!useAllocator) {
            ret = merge(
                    debugMarker(markerType, "Generating long array via new (" + size + ")"),
                    new LdcInsnNode(size),
                    new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_LONG),
                    new VarInsnNode(Opcodes.ASTORE, arrayVar.getIndex())
            );
        } else {
            ret = merge(
                    debugMarker(markerType, "Generating long array via allocator (" + size + ")"),
                    call(CONTINUATION_GETALLOCATOR_METHOD, loadVar(contVar)),    // [alloc]
                    new LdcInsnNode(size),                                       // [alloc, size]
                    invoke(FRAMEALLOCATOR_ALLOCATELONG_METHOD),                  // [array]
                    new VarInsnNode(Opcodes.ASTORE, arrayVar.getIndex())         // []
            );
        }
        
        return ret;
    }
    
    public static InsnList allocateDoubleArray(
            InstrumentationSettings settings,
            Variable contVar,
            Variable arrayVar,
            int size) {
        Validate.notNull(settings);
        Validate.notNull(contVar);
        Validate.notNull(arrayVar);
        Validate.isTrue(size > 0);

        MarkerType markerType = settings.getMarkerType();
        boolean useAllocator = settings.isCustomFrameAllocator();

        InsnList ret;

        if (!useAllocator) {
            ret = merge(
                    debugMarker(markerType, "Generating double array via new (" + size + ")"),
                    new LdcInsnNode(size),
                    new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_DOUBLE),
                    new VarInsnNode(Opcodes.ASTORE, arrayVar.getIndex())
            );
        } else {
            ret = merge(
                    debugMarker(markerType, "Generating double array via allocator (" + size + ")"),
                    call(CONTINUATION_GETALLOCATOR_METHOD, loadVar(contVar)),    // [alloc]
                    new LdcInsnNode(size),                                       // [alloc, size]
                    invoke(FRAMEALLOCATOR_ALLOCATEDOUBLE_METHOD),                // [array]
                    new VarInsnNode(Opcodes.ASTORE, arrayVar.getIndex())         // []
            );
        }
        
        return ret;
    }
    
    public static InsnList allocateObjectArray(
            InstrumentationSettings settings,
            Variable contVar,
            Variable arrayVar,
            int size) {
        Validate.notNull(settings);
        Validate.notNull(contVar);
        Validate.notNull(arrayVar);
        Validate.isTrue(size > 0);

        MarkerType markerType = settings.getMarkerType();
        boolean useAllocator = settings.isCustomFrameAllocator();

        InsnList ret;

        if (!useAllocator) {
            ret = merge(
                    debugMarker(markerType, "Generating Object array via new (" + size + ")"),
                    new LdcInsnNode(size),
                    new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"),
                    new VarInsnNode(Opcodes.ASTORE, arrayVar.getIndex())
            );
        } else {
            ret = merge(
                    debugMarker(markerType, "Generating Object array via allocator (" + size + ")"),
                    call(CONTINUATION_GETALLOCATOR_METHOD, loadVar(contVar)),    // [alloc]
                    new LdcInsnNode(size),                                       // [alloc, size]
                    invoke(FRAMEALLOCATOR_ALLOCATEOBJECT_METHOD),                // [array]
                    new VarInsnNode(Opcodes.ASTORE, arrayVar.getIndex())         // []
            );
        }
        
        return ret;
    }





    public static InsnList freeIntArray(
            InstrumentationSettings settings,
            Variable contVar,
            Variable arrayVar) {
        Validate.notNull(settings);
        Validate.notNull(contVar);
        Validate.notNull(arrayVar);

        MarkerType markerType = settings.getMarkerType();
        boolean useAllocator = settings.isCustomFrameAllocator();
        InsnList ret;

        if (!useAllocator) {
            ret = debugMarker(markerType, "Doing nothing -- int array was allocated via new");
        } else {
            ret = merge(
                    debugMarker(markerType, "Freeing int array allocated via allocator"),
                    call(CONTINUATION_GETALLOCATOR_METHOD, loadVar(contVar)),    // [alloc]
                    new VarInsnNode(Opcodes.ALOAD, arrayVar.getIndex()),         // [alloc, array]
                    invoke(FRAMEALLOCATOR_RELEASEINT_METHOD)                     // []
            );
        }
        
        return ret;
    }

    public static InsnList freeFloatArray(
            InstrumentationSettings settings,
            Variable contVar,
            Variable arrayVar) {
        Validate.notNull(settings);
        Validate.notNull(contVar);
        Validate.notNull(arrayVar);

        MarkerType markerType = settings.getMarkerType();
        boolean useAllocator = settings.isCustomFrameAllocator();
        InsnList ret;

        if (!useAllocator) {
            ret = debugMarker(markerType, "Doing nothing -- float array was allocated via new");
        } else {
            ret = merge(
                    debugMarker(markerType, "Freeing float array allocated via allocator"),
                    call(CONTINUATION_GETALLOCATOR_METHOD, loadVar(contVar)),    // [alloc]
                    new VarInsnNode(Opcodes.ALOAD, arrayVar.getIndex()),         // [alloc, array]
                    invoke(FRAMEALLOCATOR_RELEASEFLOAT_METHOD)                   // []
            );
        }
        
        return ret;
    }

    public static InsnList freeLongArray(
            InstrumentationSettings settings,
            Variable contVar,
            Variable arrayVar) {
        Validate.notNull(settings);
        Validate.notNull(contVar);
        Validate.notNull(arrayVar);

        MarkerType markerType = settings.getMarkerType();
        boolean useAllocator = settings.isCustomFrameAllocator();
        InsnList ret;

        if (!useAllocator) {
            ret = debugMarker(markerType, "Doing nothing -- long array was allocated via new");
        } else {
            ret = merge(
                    debugMarker(markerType, "Freeing long array allocated via allocator"),
                    call(CONTINUATION_GETALLOCATOR_METHOD, loadVar(contVar)),    // [alloc]
                    new VarInsnNode(Opcodes.ALOAD, arrayVar.getIndex()),         // [alloc, array]
                    invoke(FRAMEALLOCATOR_RELEASELONG_METHOD)                    // []
            );
        }
        
        return ret;
    }


    public static InsnList freeDoubleArray(
            InstrumentationSettings settings,
            Variable contVar,
            Variable arrayVar) {
        Validate.notNull(settings);
        Validate.notNull(contVar);
        Validate.notNull(arrayVar);

        MarkerType markerType = settings.getMarkerType();
        boolean useAllocator = settings.isCustomFrameAllocator();
        InsnList ret;

        if (!useAllocator) {
            ret = debugMarker(markerType, "Doing nothing -- double array was allocated via new");
        } else {
            ret = merge(
                    debugMarker(markerType, "Freeing double array allocated via allocator"),
                    call(CONTINUATION_GETALLOCATOR_METHOD, loadVar(contVar)),    // [alloc]
                    new VarInsnNode(Opcodes.ALOAD, arrayVar.getIndex()),         // [alloc, array]
                    invoke(FRAMEALLOCATOR_RELEASEDOUBLE_METHOD)                  // []
            );
        }
        
        return ret;
    }

    public static InsnList freeObjectArray(
            InstrumentationSettings settings,
            Variable contVar,
            Variable arrayVar) {
        Validate.notNull(settings);
        Validate.notNull(contVar);
        Validate.notNull(arrayVar);

        MarkerType markerType = settings.getMarkerType();
        boolean useAllocator = settings.isCustomFrameAllocator();
        InsnList ret;

        if (!useAllocator) {
            ret = debugMarker(markerType, "Doing nothing -- Object array was allocated via new");
        } else {
            ret = merge(
                    debugMarker(markerType, "Freeing Object array allocated via allocator"),
                    call(CONTINUATION_GETALLOCATOR_METHOD, loadVar(contVar)),    // [alloc]
                    new VarInsnNode(Opcodes.ALOAD, arrayVar.getIndex()),         // [alloc, array]
                    invoke(FRAMEALLOCATOR_RELEASEOBJECT_METHOD)                  // []
            );
        }
        
        return ret;
    }
}
