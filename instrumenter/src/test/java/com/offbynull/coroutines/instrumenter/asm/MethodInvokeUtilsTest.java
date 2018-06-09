package com.offbynull.coroutines.instrumenter.asm;

import org.apache.commons.lang3.reflect.MethodUtils;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;

public final class MethodInvokeUtilsTest {
    
    @Test
    public void mustProperlyDetermineStackSizeForNormalMethod() {
        Type type = Type.getType(MethodUtils.getAccessibleMethod(Integer.class, "compareTo", Integer.class));
        MethodInsnNode methodInsnNode = new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/util/Integer", "compareTo", type.getDescriptor(),
                false);
        int reqStackCount = MethodInvokeUtils.getArgumentCountRequiredForInvocation(methodInsnNode);

        assertEquals(2, reqStackCount);
    }

    @Test
    public void mustProperlyDetermineStackSizeForStaticMethod() {
        Type type = Type.getType(MethodUtils.getAccessibleMethod(Integer.class, "decode", String.class));
        MethodInsnNode methodInsnNode = new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Integer", "decode", type.getDescriptor(),
                false);
        int reqStackCount = MethodInvokeUtils.getArgumentCountRequiredForInvocation(methodInsnNode);

        assertEquals(1, reqStackCount);
    }
    
}
