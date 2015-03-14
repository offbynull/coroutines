package com.offbynull.coroutines.instrumenter;

import com.offbynull.coroutines.user.Continuation;
import java.io.IOException;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.IF_ICMPLT;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.RETURN;

public final class InstrumenterTest {

    private SingleClassLoader classLoader;

    @Before
    public void setUp() throws Exception {

        /*
         * Generates and loads the following class file:
         * 
         * package test;
         *
         * import com.offbynull.coroutines.user.Continuation;
         *
         * public class Test {
         *
         *     public void run(Continuation continuation) {
         * 	System.out.println("started!");
         * 	for (int i = 0; i < 10; i++)
         * 	    echo(continuation, i);
         *     }
         *
         *     private void echo(Continuation continuation, int x) {
         * 	System.out.println(x);
         * 	continuation.suspend();
         *     }
         * 
         * }
         */
        ClassWriter cw = new ClassWriter(0);
        FieldVisitor fv;
        MethodVisitor mv;
        AnnotationVisitor av0;

        cw.visit(52, ACC_PUBLIC + ACC_SUPER, "test/Test", null, "java/lang/Object", null);

        cw.visitSource("Test.java", null);

        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitLineNumber(5, l0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitLocalVariable("this", "Ltest/Test;", null, l0, l1, 0);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitLineNumber(15, l0);
            mv.visitInsn(RETURN);
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitLocalVariable("args", "[Ljava/lang/String;", null, l0, l1, 0);
            mv.visitMaxs(0, 1);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC, "run", "(Lcom/offbynull/coroutines/user/Continuation;)V", null, null);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitLineNumber(18, l0);
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitLdcInsn("started!");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitLineNumber(19, l1);
            mv.visitInsn(ICONST_0);
            mv.visitVarInsn(ISTORE, 2);
            Label l2 = new Label();
            mv.visitLabel(l2);
            Label l3 = new Label();
            mv.visitJumpInsn(GOTO, l3);
            Label l4 = new Label();
            mv.visitLabel(l4);
            mv.visitLineNumber(20, l4);
            mv.visitFrame(Opcodes.F_NEW, 3, new Object[]{"test/Test", "com/offbynull/coroutines/user/Continuation", Opcodes.INTEGER}, 0, new Object[]{});
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ILOAD, 2);
            mv.visitMethodInsn(INVOKESPECIAL, "test/Test", "echo", "(Lcom/offbynull/coroutines/user/Continuation;I)V", false);
            Label l5 = new Label();
            mv.visitLabel(l5);
            mv.visitLineNumber(19, l5);
            mv.visitIincInsn(2, 1);
            mv.visitLabel(l3);
            mv.visitFrame(Opcodes.F_NEW, 3, new Object[]{"test/Test", "com/offbynull/coroutines/user/Continuation", Opcodes.INTEGER}, 0, new Object[]{});
            mv.visitVarInsn(ILOAD, 2);
            mv.visitIntInsn(BIPUSH, 10);
            mv.visitJumpInsn(IF_ICMPLT, l4);
            Label l6 = new Label();
            mv.visitLabel(l6);
            mv.visitLineNumber(21, l6);
            mv.visitInsn(RETURN);
            Label l7 = new Label();
            mv.visitLabel(l7);
            mv.visitLocalVariable("this", "Ltest/Test;", null, l0, l7, 0);
            mv.visitLocalVariable("continuation", "Lcom/offbynull/coroutines/user/Continuation;", null, l0, l7, 1);
            mv.visitLocalVariable("i", "I", null, l2, l6, 2);
            mv.visitMaxs(3, 3);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PRIVATE, "echo", "(Lcom/offbynull/coroutines/user/Continuation;I)V", null, null);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitLineNumber(23, l0);
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitVarInsn(ILOAD, 2);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitLineNumber(24, l1);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEVIRTUAL, "com/offbynull/coroutines/user/Continuation", "suspend", "()V", false);
            Label l2 = new Label();
            mv.visitLabel(l2);
            mv.visitLineNumber(25, l2);
            mv.visitInsn(RETURN);
            Label l3 = new Label();
            mv.visitLabel(l3);
            mv.visitLocalVariable("this", "Ltest/Test;", null, l0, l3, 0);
            mv.visitLocalVariable("continuation", "Lcom/offbynull/coroutines/user/Continuation;", null, l0, l3, 1);
            mv.visitLocalVariable("x", "I", null, l0, l3, 2);
            mv.visitMaxs(2, 3);
            mv.visitEnd();
        }
        cw.visitEnd();

        byte[] normalClass = cw.toByteArray();
        byte[] instrumentedClass = new Instrumenter().instrument(normalClass);
        classLoader = SingleClassLoader.create(getClass().getClassLoader(), "test.Test", instrumentedClass);
    }

    @After
    public void tearDown() throws IOException {
        classLoader.close();
    }

    @Test
    public void testSomeMethod() throws Exception {
        Class<?> cls = classLoader.loadClass("test.Test");
        Object instance = cls.newInstance();
        
        Continuation continuation = new Continuation();
        MethodUtils.invokeMethod(instance, "run", continuation);
        MethodUtils.invokeMethod(instance, "run", continuation);
        MethodUtils.invokeMethod(instance, "run", continuation);
        MethodUtils.invokeMethod(instance, "run", continuation);
        MethodUtils.invokeMethod(instance, "run", continuation);
        MethodUtils.invokeMethod(instance, "run", continuation);
        MethodUtils.invokeMethod(instance, "run", continuation);
        MethodUtils.invokeMethod(instance, "run", continuation);
        MethodUtils.invokeMethod(instance, "run", continuation);
        MethodUtils.invokeMethod(instance, "run", continuation);
    }

}
