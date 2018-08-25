package com.offbynull.coroutines.instrumenter.asm;

import java.io.Serializable;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

public class TypeUtilsTest {
    
    private static ClassResourceClassInformationRepository repo;

    @BeforeAll
    public static void beforeClass() throws Exception {
        repo = new ClassResourceClassInformationRepository(TypeUtilsTest.class.getClassLoader());
    }
    
    @AfterAll
    public static void afterClass() {
        repo = null;
    }

    @Test
    public void mustIdentifyAssignabilityOfEqualTypes() {
        check(Object.class, Object.class);
        check(Comparable.class, Comparable.class);
        check(Number.class, Number.class);
        check(Integer.class, Integer.class);
        check(int.class, int.class);
        check(byte.class, byte.class);
        check(double.class, double.class);
        check(String[][].class, String[][].class);
        check(Integer[].class, Integer[].class);
        check(int[].class, int[].class);
    }

    @Test
    public void mustIdentifyAssignabilityToObject() {
        check(Object.class, Comparable.class);
        check(Object.class, Number.class);
        check(Object.class, Integer.class);
        check(Object.class, int.class);
        check(Object.class, byte.class);
        check(Object.class, double.class);
        check(Object.class, Integer[].class);
        check(Object.class, int[].class);
    }

    @Test
    public void mustIdentifyAssignabilityFromObject() {
//        check(Comparable.class, Object.class); // this won't pass, because ASM seems to have code that treats any interface as Object
                                                 // see mustIdentifyInterfaceAsObject()
        check(Number.class, Object.class);
        check(Integer.class, Object.class);
        check(int.class, Object.class);
        check(byte.class, Object.class);
        check(double.class, Object.class);
        check(Integer[].class, Object.class);
        check(int[].class, Object.class);
    }
    
    @Test
    public void mustIdentifyAssignabilityToParent() {
        check(Serializable.class, Number.class);
        check(Number.class, Integer.class);
        check(Number[].class, Integer[].class);
    }

    @Test
    public void mustIdentifyAssignabilityToChild() {
        check(Number.class, Serializable.class);
        check(Integer.class, Number.class);
        check(Integer[].class, Number[].class);
    }

    @Test
    public void mustIdentifyAssignabilityToParentArrays() {
        check(Number[][].class, Integer[][].class);
        check(Serializable[][].class, Number[][].class);
        check(Object[][].class, Serializable[][].class);
    }

    @Test
    public void mustIdentifyAssignabilityToChildArrays() {
        check(Integer[][].class, Number[][].class);
        check(Number[][].class, Serializable[][].class);
        check(Serializable[][].class, Object[][].class);
    }
    
    @Test
    public void mustIdentifyAssignabilityToArraysOfLesserDimensions() {
        check(Number[][].class, Integer[][][].class);
        check(Integer[][].class, Number[][][].class);
        check(Integer[][].class, Number[][][].class);
        check(int[][].class, int[][][].class);
        check(Object[][].class, Integer[][][].class); //note: allowed
        check(Object[][].class, Number[][][].class); //note: allowed
    }

    @Test
    public void mustIdentifyAssignabilityToArraysOfHigherDimensions() {
        check(Number[][][][].class, Integer[][][].class);
        check(Integer[][][][].class, Number[][][].class);
        check(Integer[][][][].class, Number[][][].class);
        check(int[][][][].class, int[][][].class);
        check(Object[][][][].class, Integer[][][].class);
        check(Object[][][][].class, Number[][][].class);
    }

    @Test
    public void mustIdentifyAssignabilityToArraysOfObject() {
        check(Object.class, Integer[][][].class);
        check(Object[].class, Integer[][][].class);
        check(Object[][].class, Integer[][][].class);
        check(Object[][][].class, Integer[][][].class);
        check(Object[][][][].class, Integer[][][].class); //note: not allowed?
    }

    @Test
    public void mustIdentifyAssignabilityBetweenPrimitiveArrays() {
        check(int[].class, int[].class);
        check(int[].class, int[][].class);
        check(int[][].class, int[].class);
        check(byte[].class, int[].class);
        check(int[].class, byte[].class);
        check(float[].class, int[].class);
        check(byte[].class, float[].class);
        check(Object[].class, float[].class); // Object[] not primitive but putting here anyways
        check(byte[].class, Object[].class); // Object[] not primitive but putting here anyways
    }


    public void check(Class<?> tCls, Class<?> uCls) {        
        boolean expected = tCls.isAssignableFrom(uCls);
        boolean actual = TypeUtils.isAssignableFrom(repo, Type.getType(tCls), Type.getType(uCls));
        
        assertEquals(expected, actual);
    }

    public void check(Class<?> tCls, Class<?> uCls, boolean expected) {        
        boolean actual = TypeUtils.isAssignableFrom(repo, Type.getType(tCls), Type.getType(uCls));
        
        assertEquals(expected, actual);
    }
}
