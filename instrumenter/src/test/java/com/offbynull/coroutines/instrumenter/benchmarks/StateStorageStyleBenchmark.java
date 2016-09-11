package com.offbynull.coroutines.instrumenter.benchmarks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class StateStorageStyleBenchmark {
    private static final int ROUNDS = 100000;
    private static final Random RANDOM = new Random();
    
    // HERE ARE THE RESULTS OF THIS TEST ON JAVA8 HOME PC
    // FYI: Sometimes Loop Bytearray is the fastest, but it isn't happening consistently enough to be relied on.
    //
    // Loop Bytearray:[2137, 1981, 1096, 864, 1704]
    // Boxing/Unboxing Into Object[]:[2404, 2454, 1465, 2287, 2126]
    // Each primitives type in its own array (all ints in 1 holder):[1477, 1549, 781, 1528, 1461]
    // Each primitives type in its own array:[1399, 1607, 786, 1306, 1523]
    // Each primitives type in its own array via holder:[2959, 3546, 2250, 2274, 3163]
    public static void main(String[] args) {
        long startTime;
        long endTime;


        List<Long> diffTimes1 = new ArrayList<>();
        List<Long> diffTimes2 = new ArrayList<>();
        List<Long> diffTimes3 = new ArrayList<>();
        List<Long> diffTimes4 = new ArrayList<>();
        List<Long> diffTimes5 = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            startTime = System.currentTimeMillis();
            testByteArray();
            endTime = System.currentTimeMillis();
            diffTimes1.add(endTime - startTime);
            
            
            
            
            startTime = System.currentTimeMillis();
            testObjectArray();
            endTime = System.currentTimeMillis();
            diffTimes2.add(endTime - startTime);
            
    
            
            
            startTime = System.currentTimeMillis();
            testStandardIntArray();
            endTime = System.currentTimeMillis();
            diffTimes3.add(endTime - startTime);
            
    
            
            
            startTime = System.currentTimeMillis();
            testStandardArray();
            endTime = System.currentTimeMillis();
            diffTimes4.add(endTime - startTime);
            
    
            
            
            startTime = System.currentTimeMillis();
            testStandardArrayViaHolder();
            endTime = System.currentTimeMillis();
            diffTimes5.add(endTime - startTime);
        }
        
        System.out.println("Loop Bytearray:" + diffTimes1);
        System.out.println("Boxing/Unboxing Into Object[]:" + diffTimes2);
        System.out.println("Each primitives type in its own array (all ints in 1 holder):" + diffTimes3);
        System.out.println("Each primitives type in its own array:" + diffTimes4);
        System.out.println("Each primitives type in its own array via holder:" + diffTimes5);
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    public static void testStandardIntArray() {
        for (int num = 0; num < ROUNDS; num++) {
            int[] intHolder = new int[4 * 10];
            long[] longHolder = new long[1 * 10];
            float[] floatHolder = new float[1 * 10];
            double[] doubleHolder = new double[1 * 10];
            Object[] objHolder = new Object[1 * 10];
            
            byte b1 = (byte) RANDOM.nextInt();
            char c1 = (char) RANDOM.nextInt();
            short s1 = (short) RANDOM.nextInt();
            int i1 = RANDOM.nextInt();
            long l1 = RANDOM.nextLong();
            float f1 = RANDOM.nextFloat();
            double d1 = RANDOM.nextDouble();
            Object o1 = "ffffffffffffffff";
            byte b2 = (byte) RANDOM.nextInt();
            char c2 = (char) RANDOM.nextInt();
            short s2 = (short) RANDOM.nextInt();
            int i2 = RANDOM.nextInt();
            long l2 = RANDOM.nextLong();
            float f2 = RANDOM.nextFloat();
            double d2 = RANDOM.nextDouble();
            Object o2 = "SDfsdfsdfsdfsdf";
            byte b3 = (byte) RANDOM.nextInt();
            char c3 = (char) RANDOM.nextInt();
            short s3 = (short) RANDOM.nextInt();
            int i3 = RANDOM.nextInt();
            long l3 = RANDOM.nextLong();
            float f3 = RANDOM.nextFloat();
            double d3 = RANDOM.nextDouble();
            Object o3 = ">>>>>>>>>>>>>>>>>>>>";
            byte b4 = (byte) RANDOM.nextInt();
            char c4 = (char) RANDOM.nextInt();
            short s4 = (short) RANDOM.nextInt();
            int i4 = RANDOM.nextInt();
            long l4 = RANDOM.nextLong();
            float f4 = RANDOM.nextFloat();
            double d4 = RANDOM.nextDouble();
            Object o4 = ">>>>>>>>>>>>>>>>>8>>>";
            byte b5 = (byte) RANDOM.nextInt();
            char c5 = (char) RANDOM.nextInt();
            short s5 = (short) RANDOM.nextInt();
            int i5 = RANDOM.nextInt();
            long l5 = RANDOM.nextLong();
            float f5 = RANDOM.nextFloat();
            double d5 = RANDOM.nextDouble();
            Object o5 = ">>>>>>>>>>>>>>>7>>>>>";
            byte b6 = (byte) RANDOM.nextInt();
            char c6 = (char) RANDOM.nextInt();
            short s6 = (short) RANDOM.nextInt();
            int i6 = RANDOM.nextInt();
            long l6 = RANDOM.nextLong();
            float f6 = RANDOM.nextFloat();
            double d6 = RANDOM.nextDouble();
            Object o6 = ">>>>>>>>>>>>>6>>>>>>>";
            byte b7 = (byte) RANDOM.nextInt();
            char c7 = (char) RANDOM.nextInt();
            short s7 = (short) RANDOM.nextInt();
            int i7 = RANDOM.nextInt();
            long l7 = RANDOM.nextLong();
            float f7 = RANDOM.nextFloat();
            double d7 = RANDOM.nextDouble();
            Object o7 = ">>>>>>>>>>>5>>>>>>>>>";
            byte b8 = (byte) RANDOM.nextInt();
            char c8 = (char) RANDOM.nextInt();
            short s8 = (short) RANDOM.nextInt();
            int i8 = RANDOM.nextInt();
            long l8 = RANDOM.nextLong();
            float f8 = RANDOM.nextFloat();
            double d8 = RANDOM.nextDouble();
            Object o8 = ">>>>>>>>>4>>>>>>>>>>>";
            byte b9 = (byte) RANDOM.nextInt();
            char c9 = (char) RANDOM.nextInt();
            short s9 = (short) RANDOM.nextInt();
            int i9 = RANDOM.nextInt();
            long l9 = RANDOM.nextLong();
            float f9 = RANDOM.nextFloat();
            double d9 = RANDOM.nextDouble();
            Object o9 = ">>>>>>>>>>3>>>>>>>>>>";
            byte b10 = (byte) RANDOM.nextInt();
            char c10 = (char) RANDOM.nextInt();
            short s10 = (short) RANDOM.nextInt();
            int i10 = RANDOM.nextInt();
            long l10 = RANDOM.nextLong();
            float f10 = RANDOM.nextFloat();
            double d10 = RANDOM.nextDouble();
            Object o10 = ">>>>>>>>>>>>>>>>1>>>>";
            
            intHolder[0 * 4 + 0] = b1;
            intHolder[0 * 4 + 1] = c1;
            intHolder[0 * 4 + 2] = s1;
            intHolder[0 * 4 + 3] = i1;
            longHolder[0] = l1;
            floatHolder[0] = f1;
            doubleHolder[0] = d1;
            objHolder[0] = o1;
            intHolder[1 * 4 + 0] = b2;
            intHolder[1 * 4 + 1] = c2;
            intHolder[1 * 4 + 2] = s2;
            intHolder[1 * 4 + 3] = i2;
            longHolder[1] = l2;
            floatHolder[1] = f2;
            doubleHolder[1] = d2;
            objHolder[1 * 0] = o2;
            intHolder[2 * 4 + 0] = b3;
            intHolder[2 * 4 + 1] = c3;
            intHolder[2 * 4 + 2] = s3;
            intHolder[2 * 4 + 3] = i3;
            longHolder[2] = l3;
            floatHolder[2] = f3;
            doubleHolder[2] = d3;
            objHolder[2] = o3;
            intHolder[3 * 4 + 0] = b4;
            intHolder[3 * 4 + 1] = c4;
            intHolder[3 * 4 + 2] = s4;
            intHolder[3 * 4 + 3] = i4;
            longHolder[3] = l4;
            floatHolder[3] = f4;
            doubleHolder[3] = d4;
            objHolder[3] = o4;
            intHolder[4 * 4 + 0] = b5;
            intHolder[4 * 4 + 1] = c5;
            intHolder[4 * 4 + 2] = s5;
            intHolder[4 * 4 + 3] = i5;
            longHolder[4] = l5;
            floatHolder[4] = f5;
            doubleHolder[4] = d5;
            objHolder[4] = o5;
            intHolder[5 * 4 + 0] = b6;
            intHolder[5 * 4 + 1] = c6;
            intHolder[5 * 4 + 2] = s6;
            intHolder[5 * 4 + 3] = i6;
            longHolder[5] = l6;
            floatHolder[5] = f6;
            doubleHolder[5] = d6;
            objHolder[5] = o6;
            intHolder[6 * 4 + 0] = b7;
            intHolder[6 * 4 + 1] = c7;
            intHolder[6 * 4 + 2] = s7;
            intHolder[6 * 4 + 3] = i7;
            longHolder[6] = l7;
            floatHolder[6] = f7;
            doubleHolder[6] = d7;
            objHolder[6] = o7;
            intHolder[7 * 4 + 0] = b8;
            intHolder[7 * 4 + 1] = c8;
            intHolder[7 * 4 + 2] = s8;
            intHolder[7 * 4 + 3] = i8;
            longHolder[7] = l8;
            floatHolder[7] = f8;
            doubleHolder[7] = d8;
            objHolder[7] = o8;
            intHolder[8 * 4 + 0] = b9;
            intHolder[8 * 4 + 1] = c9;
            intHolder[8 * 4 + 2] = s9;
            intHolder[8 * 4 + 3] = i9;
            longHolder[8] = l9;
            floatHolder[8] = f9;
            doubleHolder[8] = d9;
            objHolder[8] = o9;
            intHolder[9 * 4 + 0] = b10;
            intHolder[9 * 4 + 1] = c10;
            intHolder[9 * 4 + 2] = s10;
            intHolder[9 * 4 + 3] = i10;
            longHolder[9] = l10;
            floatHolder[9] = f10;
            doubleHolder[9] = d10;
            objHolder[9] = o10;

            
            if (shouldHaveSomeFakeLogicToAlwaysReturnFalse(intHolder)) {
                throw new RuntimeException();
            }
            
            b1 = (byte) intHolder[0 * 4 + 0];
            c1 = (char) intHolder[0 * 4 + 1];
            s1 = (short) intHolder[0 * 4 + 2];
            i1 = intHolder[0 * 4 + 3];
            l1 = longHolder[0];
            f1 = floatHolder[0];
            d1 = doubleHolder[0];
            o1 = objHolder[0 * 0];
            b2 = (byte) intHolder[1 * 4 + 0];
            c2 = (char) intHolder[1 * 4 + 1];
            s2 = (short) intHolder[1 * 4 + 2];
            i2 = intHolder[1 * 4 + 3];
            l2 = longHolder[1];
            f2 = floatHolder[1];
            d2 = doubleHolder[1];
            o2 = objHolder[1];
            b3 = (byte) intHolder[2 * 4 + 0];
            c3 = (char) intHolder[2 * 4 + 1];
            s3 = (short) intHolder[2 * 4 + 2];
            i3 = intHolder[2 * 4 + 3];
            l3 = longHolder[2];
            f3 = floatHolder[2];
            d3 = doubleHolder[2];
            o3 = objHolder[2];
            b4 = (byte) intHolder[3 * 4 + 0];
            c4 = (char) intHolder[3 * 4 + 1];
            s4 = (short) intHolder[3 * 4 + 2];
            i4 = intHolder[3 * 4 + 3];
            l4 = longHolder[3];
            f4 = floatHolder[3];
            d4 = doubleHolder[3];
            o4 = objHolder[3];
            b5 = (byte) intHolder[4 * 4 + 0];
            c5 = (char) intHolder[4 * 4 + 1];
            s5 = (short) intHolder[4 * 4 + 2];
            i5 = intHolder[4 * 4 + 3];
            l5 = longHolder[4];
            f5 = floatHolder[4];
            d5 = doubleHolder[4];
            o5 = objHolder[4];
            b6 = (byte) intHolder[5 * 4 + 0];
            c6 = (char) intHolder[5 * 4 + 1];
            s6 = (short) intHolder[5 * 4 + 2];
            i6 = intHolder[5 * 4 + 3];
            l6 = longHolder[5];
            f6 = floatHolder[5];
            d6 = doubleHolder[5];
            o6 = objHolder[5];
            b7 = (byte) intHolder[6 * 4 + 0];
            c7 = (char) intHolder[6 * 4 + 1];
            s7 = (short) intHolder[6 * 4 + 2];
            i7 = intHolder[6 * 4 + 3];
            l7 = longHolder[6];
            f7 = floatHolder[6];
            d7 = doubleHolder[6];
            o7 = objHolder[6];
            b8 = (byte) intHolder[7 * 4 + 0];
            c8 = (char) intHolder[7 * 4 + 1];
            s8 = (short) intHolder[7 * 4 + 2];
            i8 = intHolder[7 * 4 + 3];
            l8 = longHolder[7];
            f8 = floatHolder[7];
            d8 = doubleHolder[7];
            o8 = objHolder[7];
            b9 = (byte) intHolder[8 * 4 + 0];
            c9 = (char) intHolder[8 * 4 + 1];
            s9 = (short) intHolder[8 * 4 + 2];
            i9 = intHolder[8 * 4 + 3];
            l9 = longHolder[8];
            f9 = floatHolder[8];
            d9 = doubleHolder[8];
            o9 = objHolder[8];
            b10 = (byte) intHolder[9 * 4 + 0];
            c10 = (char) intHolder[9 * 4 + 1];
            s10 = (short) intHolder[9 * 4 + 2];
            i10 = intHolder[9 * 4 + 3];
            l10 = longHolder[9];
            f10 = floatHolder[9];
            d10 = doubleHolder[9];
            o10 = objHolder[9];

            
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, b1);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, c1);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, s1);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, i1);
            shouldHaveSomeFakeLogicToConsumeValue(longHolder, l1);
            shouldHaveSomeFakeLogicToConsumeValue(floatHolder, f1);
            shouldHaveSomeFakeLogicToConsumeValue(doubleHolder, d1);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, b2);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, c2);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, s2);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, i2);
            shouldHaveSomeFakeLogicToConsumeValue(longHolder, l2);
            shouldHaveSomeFakeLogicToConsumeValue(floatHolder, f2);
            shouldHaveSomeFakeLogicToConsumeValue(doubleHolder, d2);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, b3);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, c3);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, s3);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, i3);
            shouldHaveSomeFakeLogicToConsumeValue(longHolder, l3);
            shouldHaveSomeFakeLogicToConsumeValue(floatHolder, f3);
            shouldHaveSomeFakeLogicToConsumeValue(doubleHolder, d3);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, b4);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, c4);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, s4);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, i4);
            shouldHaveSomeFakeLogicToConsumeValue(longHolder, l4);
            shouldHaveSomeFakeLogicToConsumeValue(floatHolder, f4);
            shouldHaveSomeFakeLogicToConsumeValue(doubleHolder, d4);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, b5);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, c5);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, s5);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, i5);
            shouldHaveSomeFakeLogicToConsumeValue(longHolder, l5);
            shouldHaveSomeFakeLogicToConsumeValue(floatHolder, f5);
            shouldHaveSomeFakeLogicToConsumeValue(doubleHolder, d5);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, b6);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, c6);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, s6);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, i6);
            shouldHaveSomeFakeLogicToConsumeValue(longHolder, l6);
            shouldHaveSomeFakeLogicToConsumeValue(floatHolder, f6);
            shouldHaveSomeFakeLogicToConsumeValue(doubleHolder, d6);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, b7);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, c7);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, s7);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, i7);
            shouldHaveSomeFakeLogicToConsumeValue(longHolder, l7);
            shouldHaveSomeFakeLogicToConsumeValue(floatHolder, f7);
            shouldHaveSomeFakeLogicToConsumeValue(doubleHolder, d7);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, b8);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, c8);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, s8);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, i8);
            shouldHaveSomeFakeLogicToConsumeValue(longHolder, l8);
            shouldHaveSomeFakeLogicToConsumeValue(floatHolder, f8);
            shouldHaveSomeFakeLogicToConsumeValue(doubleHolder, d8);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, b9);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, c9);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, s9);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, i9);
            shouldHaveSomeFakeLogicToConsumeValue(longHolder, l9);
            shouldHaveSomeFakeLogicToConsumeValue(floatHolder, f9);
            shouldHaveSomeFakeLogicToConsumeValue(doubleHolder, d9);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, b10);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, c10);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, s10);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, i10);
            shouldHaveSomeFakeLogicToConsumeValue(longHolder, l10);
            shouldHaveSomeFakeLogicToConsumeValue(floatHolder, f10);
            shouldHaveSomeFakeLogicToConsumeValue(doubleHolder, d10);
        }
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    public static void testStandardArray() {
        for (int num = 0; num < ROUNDS; num++) {
            byte[] byteHolder = new byte[1 * 10];
            char[] charHolder = new char[1 * 10];
            short[] shortHolder = new short[1 * 10];
            int[] intHolder = new int[1 * 10];
            long[] longHolder = new long[1 * 10];
            float[] floatHolder = new float[1 * 10];
            double[] doubleHolder = new double[1 * 10];
            Object[] objHolder = new Object[1 * 10];
            
            byte b1 = (byte) RANDOM.nextInt();
            char c1 = (char) RANDOM.nextInt();
            short s1 = (short) RANDOM.nextInt();
            int i1 = RANDOM.nextInt();
            long l1 = RANDOM.nextLong();
            float f1 = RANDOM.nextFloat();
            double d1 = RANDOM.nextDouble();
            Object o1 = "ffffffffffffffff";
            byte b2 = (byte) RANDOM.nextInt();
            char c2 = (char) RANDOM.nextInt();
            short s2 = (short) RANDOM.nextInt();
            int i2 = RANDOM.nextInt();
            long l2 = RANDOM.nextLong();
            float f2 = RANDOM.nextFloat();
            double d2 = RANDOM.nextDouble();
            Object o2 = "SDfsdfsdfsdfsdf";
            byte b3 = (byte) RANDOM.nextInt();
            char c3 = (char) RANDOM.nextInt();
            short s3 = (short) RANDOM.nextInt();
            int i3 = RANDOM.nextInt();
            long l3 = RANDOM.nextLong();
            float f3 = RANDOM.nextFloat();
            double d3 = RANDOM.nextDouble();
            Object o3 = ">>>>>>>>>>>>>>>>>>>>";
            byte b4 = (byte) RANDOM.nextInt();
            char c4 = (char) RANDOM.nextInt();
            short s4 = (short) RANDOM.nextInt();
            int i4 = RANDOM.nextInt();
            long l4 = RANDOM.nextLong();
            float f4 = RANDOM.nextFloat();
            double d4 = RANDOM.nextDouble();
            Object o4 = ">>>>>>>>>>>>>>>>>8>>>";
            byte b5 = (byte) RANDOM.nextInt();
            char c5 = (char) RANDOM.nextInt();
            short s5 = (short) RANDOM.nextInt();
            int i5 = RANDOM.nextInt();
            long l5 = RANDOM.nextLong();
            float f5 = RANDOM.nextFloat();
            double d5 = RANDOM.nextDouble();
            Object o5 = ">>>>>>>>>>>>>>>7>>>>>";
            byte b6 = (byte) RANDOM.nextInt();
            char c6 = (char) RANDOM.nextInt();
            short s6 = (short) RANDOM.nextInt();
            int i6 = RANDOM.nextInt();
            long l6 = RANDOM.nextLong();
            float f6 = RANDOM.nextFloat();
            double d6 = RANDOM.nextDouble();
            Object o6 = ">>>>>>>>>>>>>6>>>>>>>";
            byte b7 = (byte) RANDOM.nextInt();
            char c7 = (char) RANDOM.nextInt();
            short s7 = (short) RANDOM.nextInt();
            int i7 = RANDOM.nextInt();
            long l7 = RANDOM.nextLong();
            float f7 = RANDOM.nextFloat();
            double d7 = RANDOM.nextDouble();
            Object o7 = ">>>>>>>>>>>5>>>>>>>>>";
            byte b8 = (byte) RANDOM.nextInt();
            char c8 = (char) RANDOM.nextInt();
            short s8 = (short) RANDOM.nextInt();
            int i8 = RANDOM.nextInt();
            long l8 = RANDOM.nextLong();
            float f8 = RANDOM.nextFloat();
            double d8 = RANDOM.nextDouble();
            Object o8 = ">>>>>>>>>4>>>>>>>>>>>";
            byte b9 = (byte) RANDOM.nextInt();
            char c9 = (char) RANDOM.nextInt();
            short s9 = (short) RANDOM.nextInt();
            int i9 = RANDOM.nextInt();
            long l9 = RANDOM.nextLong();
            float f9 = RANDOM.nextFloat();
            double d9 = RANDOM.nextDouble();
            Object o9 = ">>>>>>>>>>3>>>>>>>>>>";
            byte b10 = (byte) RANDOM.nextInt();
            char c10 = (char) RANDOM.nextInt();
            short s10 = (short) RANDOM.nextInt();
            int i10 = RANDOM.nextInt();
            long l10 = RANDOM.nextLong();
            float f10 = RANDOM.nextFloat();
            double d10 = RANDOM.nextDouble();
            Object o10 = ">>>>>>>>>>>>>>>>1>>>>";
            
            byteHolder[0] = b1;
            charHolder[0] = c1;
            shortHolder[0] = s1;
            intHolder[0] = i1;
            longHolder[0] = l1;
            floatHolder[0] = f1;
            doubleHolder[0] = d1;
            objHolder[0] = o1;
            byteHolder[1] = b2;
            charHolder[1] = c2;
            shortHolder[1] = s2;
            intHolder[1] = i2;
            longHolder[1] = l2;
            floatHolder[1] = f2;
            doubleHolder[1] = d2;
            objHolder[1] = o2;
            byteHolder[2] = b3;
            charHolder[2] = c3;
            shortHolder[2] = s3;
            intHolder[2] = i3;
            longHolder[2] = l3;
            floatHolder[2] = f3;
            doubleHolder[2] = d3;
            objHolder[2] = o3;
            byteHolder[3] = b4;
            charHolder[3] = c4;
            shortHolder[3] = s4;
            intHolder[3] = i4;
            longHolder[3] = l4;
            floatHolder[3] = f4;
            doubleHolder[3] = d4;
            objHolder[3] = o4;
            byteHolder[4] = b5;
            charHolder[4] = c5;
            shortHolder[4] = s5;
            intHolder[4] = i5;
            longHolder[4] = l5;
            floatHolder[4] = f5;
            doubleHolder[4] = d5;
            objHolder[4] = o5;
            byteHolder[5] = b6;
            charHolder[5] = c6;
            shortHolder[5] = s6;
            intHolder[5] = i6;
            longHolder[5] = l6;
            floatHolder[5] = f6;
            doubleHolder[5] = d6;
            objHolder[5] = o6;
            byteHolder[6] = b7;
            charHolder[6] = c7;
            shortHolder[6] = s7;
            intHolder[6] = i7;
            longHolder[6] = l7;
            floatHolder[6] = f7;
            doubleHolder[6] = d7;
            objHolder[6] = o7;
            byteHolder[7] = b8;
            charHolder[7] = c8;
            shortHolder[7] = s8;
            intHolder[7] = i8;
            longHolder[7] = l8;
            floatHolder[7] = f8;
            doubleHolder[7] = d8;
            objHolder[7] = o8;
            byteHolder[8] = b9;
            charHolder[8] = c9;
            shortHolder[8] = s9;
            intHolder[8] = i9;
            longHolder[8] = l9;
            floatHolder[8] = f9;
            doubleHolder[8] = d9;
            objHolder[8] = o9;
            byteHolder[9] = b10;
            charHolder[9] = c10;
            shortHolder[9] = s10;
            intHolder[9] = i10;
            longHolder[9] = l10;
            floatHolder[9] = f10;
            doubleHolder[9] = d10;
            objHolder[9] = o10;

            
            if (shouldHaveSomeFakeLogicToAlwaysReturnFalse(intHolder)) {
                throw new RuntimeException();
            }
            
            b1 = byteHolder[0];
            c1 = charHolder[0];
            s1 = shortHolder[0];
            i1 = intHolder[0];
            l1 = longHolder[0];
            f1 = floatHolder[0];
            d1 = doubleHolder[0];
            o1 = objHolder[0 * 0];
            b2 = byteHolder[1];
            c2 = charHolder[1];
            s2 = shortHolder[1];
            i2 = intHolder[1];
            l2 = longHolder[1];
            f2 = floatHolder[1];
            d2 = doubleHolder[1];
            o2 = objHolder[1];
            b3 = byteHolder[2];
            c3 = charHolder[2];
            s3 = shortHolder[2];
            i3 = intHolder[2];
            l3 = longHolder[2];
            f3 = floatHolder[2];
            d3 = doubleHolder[2];
            o3 = objHolder[2];
            b4 = byteHolder[3];
            c4 = charHolder[3];
            s4 = shortHolder[3];
            i4 = intHolder[3];
            l4 = longHolder[3];
            f4 = floatHolder[3];
            d4 = doubleHolder[3];
            o4 = objHolder[3];
            b5 = byteHolder[4];
            c5 = charHolder[4];
            s5 = shortHolder[4];
            i5 = intHolder[4];
            l5 = longHolder[4];
            f5 = floatHolder[4];
            d5 = doubleHolder[4];
            o5 = objHolder[4];
            b6 = byteHolder[5];
            c6 = charHolder[5];
            s6 = shortHolder[5];
            i6 = intHolder[5];
            l6 = longHolder[5];
            f6 = floatHolder[5];
            d6 = doubleHolder[5];
            o6 = objHolder[5];
            b7 = byteHolder[6];
            c7 = charHolder[6];
            s7 = shortHolder[6];
            i7 = intHolder[6];
            l7 = longHolder[6];
            f7 = floatHolder[6];
            d7 = doubleHolder[6];
            o7 = objHolder[6];
            b8 = byteHolder[7];
            c8 = charHolder[7];
            s8 = shortHolder[7];
            i8 = intHolder[7];
            l8 = longHolder[7];
            f8 = floatHolder[7];
            d8 = doubleHolder[7];
            o8 = objHolder[7];
            b9 = byteHolder[8];
            c9 = charHolder[8];
            s9 = shortHolder[8];
            i9 = intHolder[8];
            l9 = longHolder[8];
            f9 = floatHolder[8];
            d9 = doubleHolder[8];
            o9 = objHolder[8];
            b10 = byteHolder[9];
            c10 = charHolder[9];
            s10 = shortHolder[9];
            i10 = intHolder[9];
            l10 = longHolder[9];
            f10 = floatHolder[9];
            d10 = doubleHolder[9];
            o10 = objHolder[9];

            
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, b1);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, c1);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, s1);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, i1);
            shouldHaveSomeFakeLogicToConsumeValue(longHolder, l1);
            shouldHaveSomeFakeLogicToConsumeValue(floatHolder, f1);
            shouldHaveSomeFakeLogicToConsumeValue(doubleHolder, d1);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, b2);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, c2);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, s2);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, i2);
            shouldHaveSomeFakeLogicToConsumeValue(longHolder, l2);
            shouldHaveSomeFakeLogicToConsumeValue(floatHolder, f2);
            shouldHaveSomeFakeLogicToConsumeValue(doubleHolder, d2);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, b3);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, c3);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, s3);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, i3);
            shouldHaveSomeFakeLogicToConsumeValue(longHolder, l3);
            shouldHaveSomeFakeLogicToConsumeValue(floatHolder, f3);
            shouldHaveSomeFakeLogicToConsumeValue(doubleHolder, d3);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, b4);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, c4);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, s4);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, i4);
            shouldHaveSomeFakeLogicToConsumeValue(longHolder, l4);
            shouldHaveSomeFakeLogicToConsumeValue(floatHolder, f4);
            shouldHaveSomeFakeLogicToConsumeValue(doubleHolder, d4);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, b5);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, c5);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, s5);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, i5);
            shouldHaveSomeFakeLogicToConsumeValue(longHolder, l5);
            shouldHaveSomeFakeLogicToConsumeValue(floatHolder, f5);
            shouldHaveSomeFakeLogicToConsumeValue(doubleHolder, d5);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, b6);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, c6);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, s6);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, i6);
            shouldHaveSomeFakeLogicToConsumeValue(longHolder, l6);
            shouldHaveSomeFakeLogicToConsumeValue(floatHolder, f6);
            shouldHaveSomeFakeLogicToConsumeValue(doubleHolder, d6);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, b7);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, c7);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, s7);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, i7);
            shouldHaveSomeFakeLogicToConsumeValue(longHolder, l7);
            shouldHaveSomeFakeLogicToConsumeValue(floatHolder, f7);
            shouldHaveSomeFakeLogicToConsumeValue(doubleHolder, d7);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, b8);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, c8);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, s8);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, i8);
            shouldHaveSomeFakeLogicToConsumeValue(longHolder, l8);
            shouldHaveSomeFakeLogicToConsumeValue(floatHolder, f8);
            shouldHaveSomeFakeLogicToConsumeValue(doubleHolder, d8);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, b9);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, c9);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, s9);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, i9);
            shouldHaveSomeFakeLogicToConsumeValue(longHolder, l9);
            shouldHaveSomeFakeLogicToConsumeValue(floatHolder, f9);
            shouldHaveSomeFakeLogicToConsumeValue(doubleHolder, d9);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, b10);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, c10);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, s10);
            shouldHaveSomeFakeLogicToConsumeValue(intHolder, i10);
            shouldHaveSomeFakeLogicToConsumeValue(longHolder, l10);
            shouldHaveSomeFakeLogicToConsumeValue(floatHolder, f10);
            shouldHaveSomeFakeLogicToConsumeValue(doubleHolder, d10);
        }
    }

    
    
    
    
    
    
    
    
    
    
    
    public static void testByteArray() {
        for (int num = 0; num < ROUNDS; num++) {
            byte[] holder = new byte[29 * 10];
            Object[] objHolder = new Object[10];
            
            byte b1 = (byte) RANDOM.nextInt();
            char c1 = (char) RANDOM.nextInt();
            short s1 = (short) RANDOM.nextInt();
            int i1 = RANDOM.nextInt();
            long l1 = RANDOM.nextLong();
            float f1 = RANDOM.nextFloat();
            double d1 = RANDOM.nextDouble();
            Object o1 = "ffffffffffffffff";
            byte b2 = (byte) RANDOM.nextInt();
            char c2 = (char) RANDOM.nextInt();
            short s2 = (short) RANDOM.nextInt();
            int i2 = RANDOM.nextInt();
            long l2 = RANDOM.nextLong();
            float f2 = RANDOM.nextFloat();
            double d2 = RANDOM.nextDouble();
            Object o2 = "SDfsdfsdfsdfsdf";
            byte b3 = (byte) RANDOM.nextInt();
            char c3 = (char) RANDOM.nextInt();
            short s3 = (short) RANDOM.nextInt();
            int i3 = RANDOM.nextInt();
            long l3 = RANDOM.nextLong();
            float f3 = RANDOM.nextFloat();
            double d3 = RANDOM.nextDouble();
            Object o3 = ">>>>>>>>>>>>>>>>>>>>";
            byte b4 = (byte) RANDOM.nextInt();
            char c4 = (char) RANDOM.nextInt();
            short s4 = (short) RANDOM.nextInt();
            int i4 = RANDOM.nextInt();
            long l4 = RANDOM.nextLong();
            float f4 = RANDOM.nextFloat();
            double d4 = RANDOM.nextDouble();
            Object o4 = ">>>>>>>>>>>>>>>>>8>>>";
            byte b5 = (byte) RANDOM.nextInt();
            char c5 = (char) RANDOM.nextInt();
            short s5 = (short) RANDOM.nextInt();
            int i5 = RANDOM.nextInt();
            long l5 = RANDOM.nextLong();
            float f5 = RANDOM.nextFloat();
            double d5 = RANDOM.nextDouble();
            Object o5 = ">>>>>>>>>>>>>>>7>>>>>";
            byte b6 = (byte) RANDOM.nextInt();
            char c6 = (char) RANDOM.nextInt();
            short s6 = (short) RANDOM.nextInt();
            int i6 = RANDOM.nextInt();
            long l6 = RANDOM.nextLong();
            float f6 = RANDOM.nextFloat();
            double d6 = RANDOM.nextDouble();
            Object o6 = ">>>>>>>>>>>>>6>>>>>>>";
            byte b7 = (byte) RANDOM.nextInt();
            char c7 = (char) RANDOM.nextInt();
            short s7 = (short) RANDOM.nextInt();
            int i7 = RANDOM.nextInt();
            long l7 = RANDOM.nextLong();
            float f7 = RANDOM.nextFloat();
            double d7 = RANDOM.nextDouble();
            Object o7 = ">>>>>>>>>>>5>>>>>>>>>";
            byte b8 = (byte) RANDOM.nextInt();
            char c8 = (char) RANDOM.nextInt();
            short s8 = (short) RANDOM.nextInt();
            int i8 = RANDOM.nextInt();
            long l8 = RANDOM.nextLong();
            float f8 = RANDOM.nextFloat();
            double d8 = RANDOM.nextDouble();
            Object o8 = ">>>>>>>>>4>>>>>>>>>>>";
            byte b9 = (byte) RANDOM.nextInt();
            char c9 = (char) RANDOM.nextInt();
            short s9 = (short) RANDOM.nextInt();
            int i9 = RANDOM.nextInt();
            long l9 = RANDOM.nextLong();
            float f9 = RANDOM.nextFloat();
            double d9 = RANDOM.nextDouble();
            Object o9 = ">>>>>>>>>>3>>>>>>>>>>";
            byte b10 = (byte) RANDOM.nextInt();
            char c10 = (char) RANDOM.nextInt();
            short s10 = (short) RANDOM.nextInt();
            int i10 = RANDOM.nextInt();
            long l10 = RANDOM.nextLong();
            float f10 = RANDOM.nextFloat();
            double d10 = RANDOM.nextDouble();
            Object o10 = ">>>>>>>>>>>>>>>>1>>>>";

            writeOut(holder, (29 *0) + 0, b1); // +1
            writeOut(holder, (29 *0) + 1, c1); // +2
            writeOut(holder, (29 *0) + 3, s1); // +2
            writeOut(holder, (29 *0) + 5, i1); // +4
            writeOut(holder, (29 *0) + 9, l1); // +8
            writeOut(holder, (29 *0) + 17, f1); // +4
            writeOut(holder, (29 *0) + 21, d1); // +8
            objHolder[0] = o1;
            writeOut(holder, (29 *1) + 0, b2); // +1
            writeOut(holder, (29 *1) + 1, c2); // +2
            writeOut(holder, (29 *1) + 3, s2); // +2
            writeOut(holder, (29 *1) + 5, i2); // +4
            writeOut(holder, (29 *1) + 9, l2); // +8
            writeOut(holder, (29 *1) + 17, f2); // +4
            writeOut(holder, (29 *1) + 21, d2); // +8
            objHolder[1] = o2;
            writeOut(holder, (29 *2) + 0, b3); // +1
            writeOut(holder, (29 *2) + 1, c3); // +2
            writeOut(holder, (29 *2) + 3, s3); // +2
            writeOut(holder, (29 *2) + 5, i3); // +4
            writeOut(holder, (29 *2) + 9, l3); // +8
            writeOut(holder, (29 *2) + 17, f3); // +4
            writeOut(holder, (29 *2) + 21, d3); // +8
            objHolder[2] = o3;
            writeOut(holder, (29 *3) + 0, b4); // +1
            writeOut(holder, (29 *3) + 1, c4); // +2
            writeOut(holder, (29 *3) + 3, s4); // +2
            writeOut(holder, (29 *3) + 5, i4); // +4
            writeOut(holder, (29 *3) + 9, l4); // +8
            writeOut(holder, (29 *3) + 17, f4); // +4
            writeOut(holder, (29 *3) + 21, d4); // +8
            objHolder[3] = o4;
            writeOut(holder, (29 *4) + 0, b5); // +1
            writeOut(holder, (29 *4) + 1, c5); // +2
            writeOut(holder, (29 *4) + 3, s5); // +2
            writeOut(holder, (29 *4) + 5, i5); // +4
            writeOut(holder, (29 *4) + 9, l5); // +8
            writeOut(holder, (29 *4) + 17, f5); // +4
            writeOut(holder, (29 *4) + 21, d5); // +8
            objHolder[4] = o5;
            writeOut(holder, (29 *5) + 0, b6); // +1
            writeOut(holder, (29 *5) + 1, c6); // +2
            writeOut(holder, (29 *5) + 3, s6); // +2
            writeOut(holder, (29 *5) + 5, i6); // +4
            writeOut(holder, (29 *5) + 9, l6); // +8
            writeOut(holder, (29 *5) + 17, f6); // +4
            writeOut(holder, (29 *5) + 21, d6); // +8
            objHolder[5] = o6;
            writeOut(holder, (29 *6) + 0, b7); // +1
            writeOut(holder, (29 *6) + 1, c7); // +2
            writeOut(holder, (29 *6) + 3, s7); // +2
            writeOut(holder, (29 *6) + 5, i7); // +4
            writeOut(holder, (29 *6) + 9, l7); // +8
            writeOut(holder, (29 *6) + 17, f7); // +4
            writeOut(holder, (29 *6) + 21, d7); // +8
            objHolder[6] = o7;
            writeOut(holder, (29 *7) + 0, b8); // +1
            writeOut(holder, (29 *7) + 1, c8); // +2
            writeOut(holder, (29 *7) + 3, s8); // +2
            writeOut(holder, (29 *7) + 5, i8); // +4
            writeOut(holder, (29 *7) + 9, l8); // +8
            writeOut(holder, (29 *7) + 17, f8); // +4
            writeOut(holder, (29 *7) + 21, d8); // +8
            objHolder[7] = o8;
            writeOut(holder, (29 *8) + 0, b9); // +1
            writeOut(holder, (29 *8) + 1, c9); // +2
            writeOut(holder, (29 *8) + 3, s9); // +2
            writeOut(holder, (29 *8) + 5, i9); // +4
            writeOut(holder, (29 *8) + 9, l9); // +8
            writeOut(holder, (29 *8) + 17, f9); // +4
            writeOut(holder, (29 *8) + 21, d9); // +8
            objHolder[8] = o9;
            writeOut(holder, (29 *9) + 0, b10); // +1
            writeOut(holder, (29 *9) + 1, c10); // +2
            writeOut(holder, (29 *9) + 3, s10); // +2
            writeOut(holder, (29 *9) + 5, i10); // +4
            writeOut(holder, (29 *9) + 9, l10); // +8
            writeOut(holder, (29 *9) + 17, f10); // +4
            writeOut(holder, (29 *9) + 21, d10); // +8
            objHolder[9] = o10;
            
            if (shouldHaveSomeFakeLogicToAlwaysReturnFalse(holder)) {
                throw new RuntimeException();
            }
            
            b1 = readOutByte(holder, 0);
            c1 = readOutChar(holder, 1);
            s1 = readOutShort(holder, 3);
            i1 = readOutInt(holder, 5);
            l1 = readOutLong(holder, 9);
            f1 = readOutFloat(holder, 17);
            d1 = readOutDouble(holder, 21);
            o1 = objHolder[0];
            b2 = readOutByte(holder, (29*1) + 0);
            c2 = readOutChar(holder, (29*1) + 1);
            s2 = readOutShort(holder, (29*1) + 3);
            i2 = readOutInt(holder, (29*1) + 5);
            l2 = readOutLong(holder, (29*1) + 9);
            f2 = readOutFloat(holder, (29*1) + 17);
            d2 = readOutDouble(holder, (29*1) + 21);
            o2 = objHolder[1];
            b3 = readOutByte(holder, (29*2) + 0);
            c3 = readOutChar(holder, (29*2) + 1);
            s3 = readOutShort(holder, (29*2) + 3);
            i3 = readOutInt(holder, (29*2) + 5);
            l3 = readOutLong(holder, (29*2) + 9);
            f3 = readOutFloat(holder, (29*2) + 17);
            d3 = readOutDouble(holder, (29*2) + 21);
            o3 = objHolder[2];
            b4 = readOutByte(holder, (29*3) + 0);
            c4 = readOutChar(holder, (29*3) + 1);
            s4 = readOutShort(holder, (29*3) + 3);
            i4 = readOutInt(holder, (29*3) + 5);
            l4 = readOutLong(holder, (29*3) + 9);
            f4 = readOutFloat(holder, (29*3) + 17);
            d4 = readOutDouble(holder, (29*3) + 21);
            o4 = objHolder[3];
            b5 = readOutByte(holder, (29*4) + 0);
            c5 = readOutChar(holder, (29*4) + 1);
            s5 = readOutShort(holder, (29*4) + 3);
            i5 = readOutInt(holder, (29*4) + 5);
            l5 = readOutLong(holder, (29*4) + 9);
            f5 = readOutFloat(holder, (29*4) + 17);
            d5 = readOutDouble(holder, (29*4) + 21);
            o5 = objHolder[4];
            b6 = readOutByte(holder, (29*5) + 0);
            c6 = readOutChar(holder, (29*5) + 1);
            s6 = readOutShort(holder, (29*5) + 3);
            i6 = readOutInt(holder, (29*5) + 5);
            l6 = readOutLong(holder, (29*5) + 9);
            f6 = readOutFloat(holder, (29*5) + 17);
            d6 = readOutDouble(holder, (29*5) + 21);
            o6 = objHolder[5];
            b7 = readOutByte(holder, (29*6) + 0);
            c7 = readOutChar(holder, (29*6) + 1);
            s7 = readOutShort(holder, (29*6) + 3);
            i7 = readOutInt(holder, (29*6) + 5);
            l7 = readOutLong(holder, (29*6) + 9);
            f7 = readOutFloat(holder, (29*6) + 17);
            d7 = readOutDouble(holder, (29*6) + 21);
            o7 = objHolder[6];
            b8 = readOutByte(holder, (29*7) + 0);
            c8 = readOutChar(holder, (29*7) + 1);
            s8 = readOutShort(holder, (29*7) + 3);
            i8 = readOutInt(holder, (29*7) + 5);
            l8 = readOutLong(holder, (29*7) + 9);
            f8 = readOutFloat(holder, (29*7) + 17);
            d8 = readOutDouble(holder, (29*7) + 21);
            o8 = objHolder[7];
            b9 = readOutByte(holder, (29*8) + 0);
            c9 = readOutChar(holder, (29*8) + 1);
            s9 = readOutShort(holder, (29*8) + 3);
            i9 = readOutInt(holder, (29*8) + 5);
            l9 = readOutLong(holder, (29*8) + 9);
            f9 = readOutFloat(holder, (29*8) + 17);
            d9 = readOutDouble(holder, (29*8) + 21);
            o9 = objHolder[8];
            b9 = readOutByte(holder, (29*9) + 0);
            c10 = readOutChar(holder, (29*9) + 1);
            s10 = readOutShort(holder, (29*9) + 3);
            i10 = readOutInt(holder, (29*9) + 5);
            l10 = readOutLong(holder, (29*9) + 9);
            f10 = readOutFloat(holder, (29*9) + 17);
            d10 = readOutDouble(holder, (29*9) + 21);
            o10 = objHolder[9];
            
            shouldHaveSomeFakeLogicToConsumeValue(holder, b1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, o1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, o2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, o3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, o4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, o5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, o6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, o7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, o8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, o9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b10);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c10);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s10);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i10);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l10);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f10);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d10);
            shouldHaveSomeFakeLogicToConsumeValue(holder, o10);
        }
    }
    
    private static void writeOut(byte[]dst, int dstOffset, byte value) {
        dst[dstOffset] = value;
    }

    private static byte readOutByte(byte[]dst, int dstOffset) {
        return dst[dstOffset];
    }
    
    private static void writeOut(byte[]dst, int dstOffset, char value) {
        for (int i = 0; i < Character.BYTES; i++) {
            dst[dstOffset + i] = (byte) (value >>> (i * 8));
        }
    }

    private static char readOutChar(byte[]dst, int dstOffset) {
        int val = 0;
        for (int i = 0; i < Character.BYTES; i++) {
            val=val<<8;
            val=val|(dst[dstOffset + i] & 0xFF);
        }
        return (char) val;
    }

    private static void writeOut(byte[]dst, int dstOffset, short value) {
        for (int i = 0; i < Short.BYTES; i++) {
            dst[dstOffset + i] = (byte) (value >> (i * 8));
        }
    }

    private static short readOutShort(byte[]dst, int dstOffset) {
        int val = 0;
        for (int i = 0; i < Short.BYTES; i++) {
            val=val<<8;
            val=val|(dst[dstOffset + i] & 0xFF);
        }
        return (short) val;
    }
    
    private static void writeOut(byte[]dst, int dstOffset, int value) {
        for (int i = 0; i < Integer.BYTES; i++) {
            dst[dstOffset + i] = (byte) (value >> (i * 8));
        }
    }

    private static int readOutInt(byte[]dst, int dstOffset) {
        int val = 0;
        for (int i = 0; i < Integer.BYTES; i++) {
            val=val<<8;
            val=val|(dst[dstOffset + i] & 0xFF);
        }
        return val;
    }
    
    private static void writeOut(byte[]dst, int dstOffset, long value) {
        for (int i = 0; i < Long.BYTES; i++) {
            dst[dstOffset + i] = (byte) (value >> (i * 8));
        }
    }

    private static long readOutLong(byte[]dst, int dstOffset) {
        long val = 0;
        for (int i = 0; i < Long.BYTES; i++) {
            val=val<<8;
            val=val|(dst[dstOffset + i] & 0xFFL);
        }
        return val;
    }

    private static void writeOut(byte[]dst, int dstOffset, float value) {
        int asInt = Float.floatToRawIntBits(value);
        writeOut(dst, dstOffset, asInt);
    }

    private static float readOutFloat(byte[]dst, int dstOffset) {
        int val = readOutInt(dst, dstOffset);
        return Float.intBitsToFloat(val);
    }

    private static void writeOut(byte[]dst, int dstOffset, double value) {
        long asLong = Double.doubleToRawLongBits(value);
        writeOut(dst, dstOffset, asLong);
    }

    private static double readOutDouble(byte[]dst, int dstOffset) {
        long val = readOutLong(dst, dstOffset);
        return Double.longBitsToDouble(val);
    }

    
    
    
    
    
    
    
    
    
    
    

    
    
    
    
    
    
    
    
    
    
    
    
    public static void testObjectArray() {
        for (int num = 0; num < ROUNDS; num++) {
            Object[] holder = new Object[8*10];
            
            byte b1 = (byte) RANDOM.nextInt();
            char c1 = (char) RANDOM.nextInt();
            short s1 = (short) RANDOM.nextInt();
            int i1 = RANDOM.nextInt();
            long l1 = RANDOM.nextLong();
            float f1 = RANDOM.nextFloat();
            double d1 = RANDOM.nextDouble();
            Object o1 = "ffffffffffffffff";
            byte b2 = (byte) RANDOM.nextInt();
            char c2 = (char) RANDOM.nextInt();
            short s2 = (short) RANDOM.nextInt();
            int i2 = RANDOM.nextInt();
            long l2 = RANDOM.nextLong();
            float f2 = RANDOM.nextFloat();
            double d2 = RANDOM.nextDouble();
            Object o2 = "SDfsdfsdfsdfsdf";
            byte b3 = (byte) RANDOM.nextInt();
            char c3 = (char) RANDOM.nextInt();
            short s3 = (short) RANDOM.nextInt();
            int i3 = RANDOM.nextInt();
            long l3 = RANDOM.nextLong();
            float f3 = RANDOM.nextFloat();
            double d3 = RANDOM.nextDouble();
            Object o3 = ">>>>>>>>>>>>>>>>>>>>";
            byte b4 = (byte) RANDOM.nextInt();
            char c4 = (char) RANDOM.nextInt();
            short s4 = (short) RANDOM.nextInt();
            int i4 = RANDOM.nextInt();
            long l4 = RANDOM.nextLong();
            float f4 = RANDOM.nextFloat();
            double d4 = RANDOM.nextDouble();
            Object o4 = ">>>>>>>>>>>>>>>>>8>>>";
            byte b5 = (byte) RANDOM.nextInt();
            char c5 = (char) RANDOM.nextInt();
            short s5 = (short) RANDOM.nextInt();
            int i5 = RANDOM.nextInt();
            long l5 = RANDOM.nextLong();
            float f5 = RANDOM.nextFloat();
            double d5 = RANDOM.nextDouble();
            Object o5 = ">>>>>>>>>>>>>>>7>>>>>";
            byte b6 = (byte) RANDOM.nextInt();
            char c6 = (char) RANDOM.nextInt();
            short s6 = (short) RANDOM.nextInt();
            int i6 = RANDOM.nextInt();
            long l6 = RANDOM.nextLong();
            float f6 = RANDOM.nextFloat();
            double d6 = RANDOM.nextDouble();
            Object o6 = ">>>>>>>>>>>>>6>>>>>>>";
            byte b7 = (byte) RANDOM.nextInt();
            char c7 = (char) RANDOM.nextInt();
            short s7 = (short) RANDOM.nextInt();
            int i7 = RANDOM.nextInt();
            long l7 = RANDOM.nextLong();
            float f7 = RANDOM.nextFloat();
            double d7 = RANDOM.nextDouble();
            Object o7 = ">>>>>>>>>>>5>>>>>>>>>";
            byte b8 = (byte) RANDOM.nextInt();
            char c8 = (char) RANDOM.nextInt();
            short s8 = (short) RANDOM.nextInt();
            int i8 = RANDOM.nextInt();
            long l8 = RANDOM.nextLong();
            float f8 = RANDOM.nextFloat();
            double d8 = RANDOM.nextDouble();
            Object o8 = ">>>>>>>>>4>>>>>>>>>>>";
            byte b9 = (byte) RANDOM.nextInt();
            char c9 = (char) RANDOM.nextInt();
            short s9 = (short) RANDOM.nextInt();
            int i9 = RANDOM.nextInt();
            long l9 = RANDOM.nextLong();
            float f9 = RANDOM.nextFloat();
            double d9 = RANDOM.nextDouble();
            Object o9 = ">>>>>>>>>>3>>>>>>>>>>";
            byte b10 = (byte) RANDOM.nextInt();
            char c10 = (char) RANDOM.nextInt();
            short s10 = (short) RANDOM.nextInt();
            int i10 = RANDOM.nextInt();
            long l10 = RANDOM.nextLong();
            float f10 = RANDOM.nextFloat();
            double d10 = RANDOM.nextDouble();
            Object o10 = ">>>>>>>>>>>>>>>>1>>>>";

            
            holder[0] = b1;
            holder[1] = c1;
            holder[2] = s1;
            holder[3] = i1;
            holder[4] = l1;
            holder[5] = f1;
            holder[6] = d1;
            holder[7] = o1;
            holder[8 + 0] = b2;
            holder[8 + 1] = c2;
            holder[8 + 2] = s2;
            holder[8 + 3] = i2;
            holder[8 + 4] = l2;
            holder[8 + 5] = f2;
            holder[8 + 6] = d2;
            holder[8 + 7] = o2;
            holder[8 + 8 + 0] = b3;
            holder[8 + 8 + 1] = c3;
            holder[8 + 8 + 2] = s3;
            holder[8 + 8 + 3] = i3;
            holder[8 + 8 + 4] = l3;
            holder[8 + 8 + 5] = f3;
            holder[8 + 8 + 6] = d3;
            holder[8 + 8 + 7] = o3;
            holder[8 + 8 + 8 + 0] = b4;
            holder[8 + 8 + 8 + 1] = c4;
            holder[8 + 8 + 8 + 2] = s4;
            holder[8 + 8 + 8 + 3] = i4;
            holder[8 + 8 + 8 + 4] = l4;
            holder[8 + 8 + 8 + 5] = f4;
            holder[8 + 8 + 8 + 6] = d4;
            holder[8 + 8 + 8 + 7] = o4;
            holder[8 + 8 + 8 + 8 + 0] = b5;
            holder[8 + 8 + 8 + 8 + 1] = c5;
            holder[8 + 8 + 8 + 8 + 2] = s5;
            holder[8 + 8 + 8 + 8 + 3] = i5;
            holder[8 + 8 + 8 + 8 + 4] = l5;
            holder[8 + 8 + 8 + 8 + 5] = f5;
            holder[8 + 8 + 8 + 8 + 6] = d5;
            holder[8 + 8 + 8 + 8 + 7] = o5;
            holder[8 + 8 + 8 + 8 + 8 + 0] = b6;
            holder[8 + 8 + 8 + 8 + 8 + 1] = c6;
            holder[8 + 8 + 8 + 8 + 8 + 2] = s6;
            holder[8 + 8 + 8 + 8 + 8 + 3] = i6;
            holder[8 + 8 + 8 + 8 + 8 + 4] = l6;
            holder[8 + 8 + 8 + 8 + 8 + 5] = f6;
            holder[8 + 8 + 8 + 8 + 8 + 6] = d6;
            holder[8 + 8 + 8 + 8 + 8 + 7] = o6;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 0] = b7;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 1] = c7;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 2] = s7;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 3] = i7;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 4] = l7;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 5] = f7;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 6] = d7;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 7] = o7;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 0] = b8;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 1] = c8;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 2] = s8;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 3] = i8;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 4] = l8;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 5] = f8;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 6] = d8;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 7] = o8;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 0] = b9;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 1] = c9;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 2] = s9;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 3] = i9;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 4] = l9;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 5] = f9;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 6] = d9;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 7] = o9;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 0] = b10;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 1] = c10;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 2] = s10;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 3] = i10;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 4] = l10;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 5] = f10;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 6] = d10;
            holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 7] = o10;
            
            if (shouldHaveSomeFakeLogicToAlwaysReturnFalse(holder)) {
                throw new RuntimeException();
            }
            
            b1 = (byte) holder   [0];
            c1 = (char) holder   [1];
            s1 = (short) holder  [2];
            i1 = (int) holder    [3];
            l1 = (long) holder   [4];
            f1 = (float) holder  [5];
            d1 = (double) holder [6];
            o1 = (String) holder [7];
            b2 = (byte) holder   [8 + 0];
            c2 = (char) holder   [8 + 1];
            s2 = (short) holder  [8 + 2];
            i2 = (int) holder    [8 + 3];
            l2 = (long) holder   [8 + 4];
            f2 = (float) holder  [8 + 5];
            d2 = (double) holder [8 + 6];
            o2 = (String) holder [8 + 7];
            b3 = (byte) holder   [8 + 8 +0];
            c3 = (char) holder   [8 + 8 + 1];
            s3 = (short) holder  [8 + 8 + 2];
            i3 = (int) holder    [8 + 8 + 3];
            l3 = (long) holder   [8 + 8 + 4];
            f3 = (float) holder  [8 + 8 + 5];
            d3 = (double) holder [8 + 8 + 6];
            o3 = (String) holder [8 + 8 + 7];
            b4 = (byte) holder   [8 + 8 + 8 +0];
            c4 = (char) holder   [8 + 8 + 8 + 1];
            s4 = (short) holder  [8 + 8 + 8 + 2];
            i4 = (int) holder    [8 + 8 + 8 + 3];
            l4 = (long) holder   [8 + 8 + 8 + 4];
            f4 = (float) holder  [8 + 8 + 8 + 5];
            d4 = (double) holder [8 + 8 + 8 + 6];
            o4 = (String) holder [8 + 8 + 8 + 7];
            b5 = (byte) holder   [8 + 8 + 8 + 8 +0];
            c5 = (char) holder   [8 + 8 + 8 + 8 + 1];
            s5 = (short) holder  [8 + 8 + 8 + 8 + 2];
            i5 = (int) holder    [8 + 8 + 8 + 8 + 3];
            l5 = (long) holder   [8 + 8 + 8 + 8 + 4];
            f5 = (float) holder  [8 + 8 + 8 + 8 + 5];
            d5 = (double) holder [8 + 8 + 8 + 8 + 6];
            o5 = (String) holder [8 + 8 + 8 + 8 + 7];
            b6 = (byte) holder   [8 + 8 + 8 + 8 + 8 + 0];
            c6 = (char) holder   [8 + 8 + 8 + 8 + 8 + 1];
            s6 = (short) holder  [8 + 8 + 8 + 8 + 8 + 2];
            i6 = (int) holder    [8 + 8 + 8 + 8 + 8 + 3];
            l6 = (long) holder   [8 + 8 + 8 + 8 + 8 + 4];
            f6 = (float) holder  [8 + 8 + 8 + 8 + 8 + 5];
            d6 = (double) holder [8 + 8 + 8 + 8 + 8 + 6];
            o6 = (String) holder [8 + 8 + 8 + 8 + 8 + 7];
            b7 = (byte) holder   [8 + 8 + 8 + 8 + 8 + 8 + 0];
            c7 = (char) holder   [8 + 8 + 8 + 8 + 8 + 8 + 1];
            s7 = (short) holder  [8 + 8 + 8 + 8 + 8 + 8 + 2];
            i7 = (int) holder    [8 + 8 + 8 + 8 + 8 + 8 + 3];
            l7 = (long) holder   [8 + 8 + 8 + 8 + 8 + 8 + 4];
            f7 = (float) holder  [8 + 8 + 8 + 8 + 8 + 8 + 5];
            d7 = (double) holder [8 + 8 + 8 + 8 + 8 + 8 + 6];
            o7 = (String) holder [8 + 8 + 8 + 8 + 8 + 8 + 7];
            b8 = (byte) holder   [8 + 8 + 8 + 8 + 8 + 8 + 8 + 0];
            c8 = (char) holder   [8 + 8 + 8 + 8 + 8 + 8 + 8 + 1];
            s8 = (short) holder  [8 + 8 + 8 + 8 + 8 + 8 + 8 + 2];
            i8 = (int) holder    [8 + 8 + 8 + 8 + 8 + 8 + 8 + 3];
            l8 = (long) holder   [8 + 8 + 8 + 8 + 8 + 8 + 8 + 4];
            f8 = (float) holder  [8 + 8 + 8 + 8 + 8 + 8 + 8 + 5];
            d8 = (double) holder [8 + 8 + 8 + 8 + 8 + 8 + 8 + 6];
            o8 = (String) holder [8 + 8 + 8 + 8 + 8 + 8 + 8 + 7];
            b9 = (byte) holder   [8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 0];
            c9 = (char) holder   [8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 1];
            s9 = (short) holder  [8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 2];
            i9 = (int) holder    [8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 3];
            l9 = (long) holder   [8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 4];
            f9 = (float) holder  [8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 5];
            d9 = (double) holder [8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 6];
            o9 = (String) holder [8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 7];
            b10 = (byte) holder  [8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 0];
            c10 = (char) holder  [8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 1];
            s10 = (short) holder [8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 2];
            i10 = (int) holder   [8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 3];
            l10 = (long) holder  [8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 4];
            f10 = (float) holder [8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 5];
            d10 = (double) holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 6];
            o10 = (String) holder[8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 8 + 7];
            
            shouldHaveSomeFakeLogicToConsumeValue(holder, b1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, o1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, o2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, o3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, o4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, o5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, o6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, o7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, o8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, o9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b10);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c10);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s10);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i10);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l10);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f10);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d10);
            shouldHaveSomeFakeLogicToConsumeValue(holder, o10);
        }
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    public static void testStandardArrayViaHolder() {
        for (int num = 0; num < ROUNDS; num++) {
            SimpleArrayHolder holder = new SimpleArrayHolder(1 * 10, 1 * 10, 1 * 10, 1 * 10, 1 * 10, 1 * 10, 1 * 10, 1 * 10);
            
            byte b1 = (byte) RANDOM.nextInt();
            char c1 = (char) RANDOM.nextInt();
            short s1 = (short) RANDOM.nextInt();
            int i1 = RANDOM.nextInt();
            long l1 = RANDOM.nextLong();
            float f1 = RANDOM.nextFloat();
            double d1 = RANDOM.nextDouble();
            Object o1 = "ffffffffffffffff";
            byte b2 = (byte) RANDOM.nextInt();
            char c2 = (char) RANDOM.nextInt();
            short s2 = (short) RANDOM.nextInt();
            int i2 = RANDOM.nextInt();
            long l2 = RANDOM.nextLong();
            float f2 = RANDOM.nextFloat();
            double d2 = RANDOM.nextDouble();
            Object o2 = "SDfsdfsdfsdfsdf";
            byte b3 = (byte) RANDOM.nextInt();
            char c3 = (char) RANDOM.nextInt();
            short s3 = (short) RANDOM.nextInt();
            int i3 = RANDOM.nextInt();
            long l3 = RANDOM.nextLong();
            float f3 = RANDOM.nextFloat();
            double d3 = RANDOM.nextDouble();
            Object o3 = ">>>>>>>>>>>>>>>>>>>>";
            byte b4 = (byte) RANDOM.nextInt();
            char c4 = (char) RANDOM.nextInt();
            short s4 = (short) RANDOM.nextInt();
            int i4 = RANDOM.nextInt();
            long l4 = RANDOM.nextLong();
            float f4 = RANDOM.nextFloat();
            double d4 = RANDOM.nextDouble();
            Object o4 = ">>>>>>>>>>>>>>>>>8>>>";
            byte b5 = (byte) RANDOM.nextInt();
            char c5 = (char) RANDOM.nextInt();
            short s5 = (short) RANDOM.nextInt();
            int i5 = RANDOM.nextInt();
            long l5 = RANDOM.nextLong();
            float f5 = RANDOM.nextFloat();
            double d5 = RANDOM.nextDouble();
            Object o5 = ">>>>>>>>>>>>>>>7>>>>>";
            byte b6 = (byte) RANDOM.nextInt();
            char c6 = (char) RANDOM.nextInt();
            short s6 = (short) RANDOM.nextInt();
            int i6 = RANDOM.nextInt();
            long l6 = RANDOM.nextLong();
            float f6 = RANDOM.nextFloat();
            double d6 = RANDOM.nextDouble();
            Object o6 = ">>>>>>>>>>>>>6>>>>>>>";
            byte b7 = (byte) RANDOM.nextInt();
            char c7 = (char) RANDOM.nextInt();
            short s7 = (short) RANDOM.nextInt();
            int i7 = RANDOM.nextInt();
            long l7 = RANDOM.nextLong();
            float f7 = RANDOM.nextFloat();
            double d7 = RANDOM.nextDouble();
            Object o7 = ">>>>>>>>>>>5>>>>>>>>>";
            byte b8 = (byte) RANDOM.nextInt();
            char c8 = (char) RANDOM.nextInt();
            short s8 = (short) RANDOM.nextInt();
            int i8 = RANDOM.nextInt();
            long l8 = RANDOM.nextLong();
            float f8 = RANDOM.nextFloat();
            double d8 = RANDOM.nextDouble();
            Object o8 = ">>>>>>>>>4>>>>>>>>>>>";
            byte b9 = (byte) RANDOM.nextInt();
            char c9 = (char) RANDOM.nextInt();
            short s9 = (short) RANDOM.nextInt();
            int i9 = RANDOM.nextInt();
            long l9 = RANDOM.nextLong();
            float f9 = RANDOM.nextFloat();
            double d9 = RANDOM.nextDouble();
            Object o9 = ">>>>>>>>>>3>>>>>>>>>>";
            byte b10 = (byte) RANDOM.nextInt();
            char c10 = (char) RANDOM.nextInt();
            short s10 = (short) RANDOM.nextInt();
            int i10 = RANDOM.nextInt();
            long l10 = RANDOM.nextLong();
            float f10 = RANDOM.nextFloat();
            double d10 = RANDOM.nextDouble();
            Object o10 = ">>>>>>>>>>>>>>>>1>>>>";
            
            holder.setB(0, b1);
            holder.setC(0, c1);
            holder.setS(0, s1);
            holder.setI(0, i1);
            holder.setL(0, l1);
            holder.setF(0, f1);
            holder.setD(0, d1);
            holder.setO(0, o1);
            holder.setB(1, b2);
            holder.setC(1, c2);
            holder.setS(1, s2);
            holder.setI(1, i2);
            holder.setL(1, l2);
            holder.setF(1, f2);
            holder.setD(1, d2);
            holder.setO(1, o2);
            holder.setB(2, b3);
            holder.setC(2, c3);
            holder.setS(2, s3);
            holder.setI(2, i3);
            holder.setL(2, l3);
            holder.setF(2, f3);
            holder.setD(2, d3);
            holder.setO(2, o3);
            holder.setB(3, b4);
            holder.setC(3, c4);
            holder.setS(3, s4);
            holder.setI(3, i4);
            holder.setL(3, l4);
            holder.setF(3, f4);
            holder.setD(3, d4);
            holder.setO(3, o4);
            holder.setB(4, b5);
            holder.setC(4, c5);
            holder.setS(4, s5);
            holder.setI(4, i5);
            holder.setL(4, l5);
            holder.setF(4, f5);
            holder.setD(4, d5);
            holder.setO(4, o5);
            holder.setB(5, b6);
            holder.setC(5, c6);
            holder.setS(5, s6);
            holder.setI(5, i6);
            holder.setL(5, l6);
            holder.setF(5, f6);
            holder.setD(5, d6);
            holder.setO(5, o6);
            holder.setB(6, b7);
            holder.setC(6, c7);
            holder.setS(6, s7);
            holder.setI(6, i7);
            holder.setL(6, l7);
            holder.setF(6, f7);
            holder.setD(6, d7);
            holder.setO(6, o7);
            holder.setB(7, b8);
            holder.setC(7, c8);
            holder.setS(7, s8);
            holder.setI(7, i8);
            holder.setL(7, l8);
            holder.setF(7, f8);
            holder.setD(7, d8);
            holder.setO(7, o8);
            holder.setB(8, b9);
            holder.setC(8, c9);
            holder.setS(8, s9);
            holder.setI(8, i9);
            holder.setL(8, l9);
            holder.setF(8, f9);
            holder.setD(8, d9);
            holder.setO(8, o9);
            holder.setB(9, b10);
            holder.setC(9, c10);
            holder.setS(9, s10);
            holder.setI(9, i10);
            holder.setL(9, l10);
            holder.setF(9, f10);
            holder.setD(9, d10);
            holder.setO(9, o10);

            
            if (shouldHaveSomeFakeLogicToAlwaysReturnFalse(holder)) {
                throw new RuntimeException();
            }
            
            b1 = holder.getB(0);
            c1 = holder.getC(0);
            s1 = holder.getS(0);
            i1 = holder.getI(0);
            l1 = holder.getL(0);
            f1 = holder.getF(0);
            d1 = holder.getD(0);
            o1 = holder.getO(0 * 0);
            b2 = holder.getB(1);
            c2 = holder.getC(1);
            s2 = holder.getS(1);
            i2 = holder.getI(1);
            l2 = holder.getL(1);
            f2 = holder.getF(1);
            d2 = holder.getD(1);
            o2 = holder.getO(1);
            b3 = holder.getB(2);
            c3 = holder.getC(2);
            s3 = holder.getS(2);
            i3 = holder.getI(2);
            l3 = holder.getL(2);
            f3 = holder.getF(2);
            d3 = holder.getD(2);
            o3 = holder.getO(2);
            b4 = holder.getB(3);
            c4 = holder.getC(3);
            s4 = holder.getS(3);
            i4 = holder.getI(3);
            l4 = holder.getL(3);
            f4 = holder.getF(3);
            d4 = holder.getD(3);
            o4 = holder.getO(3);
            b5 = holder.getB(4);
            c5 = holder.getC(4);
            s5 = holder.getS(4);
            i5 = holder.getI(4);
            l5 = holder.getL(4);
            f5 = holder.getF(4);
            d5 = holder.getD(4);
            o5 = holder.getO(4);
            b6 = holder.getB(5);
            c6 = holder.getC(5);
            s6 = holder.getS(5);
            i6 = holder.getI(5);
            l6 = holder.getL(5);
            f6 = holder.getF(5);
            d6 = holder.getD(5);
            o6 = holder.getO(5);
            b7 = holder.getB(6);
            c7 = holder.getC(6);
            s7 = holder.getS(6);
            i7 = holder.getI(6);
            l7 = holder.getL(6);
            f7 = holder.getF(6);
            d7 = holder.getD(6);
            o7 = holder.getO(6);
            b8 = holder.getB(7);
            c8 = holder.getC(7);
            s8 = holder.getS(7);
            i8 = holder.getI(7);
            l8 = holder.getL(7);
            f8 = holder.getF(7);
            d8 = holder.getD(7);
            o8 = holder.getO(7);
            b9 = holder.getB(8);
            c9 = holder.getC(8);
            s9 = holder.getS(8);
            i9 = holder.getI(8);
            l9 = holder.getL(8);
            f9 = holder.getF(8);
            d9 = holder.getD(8);
            o9 = holder.getO(8);
            b10 = holder.getB(9);
            c10 = holder.getC(9);
            s10 = holder.getS(9);
            i10 = holder.getI(9);
            l10 = holder.getL(9);
            f10 = holder.getF(9);
            d10 = holder.getD(9);
            o10 = holder.getO(9);

            
            shouldHaveSomeFakeLogicToConsumeValue(holder, b1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d1);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d2);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d3);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d4);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d5);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d6);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d7);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d8);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d9);
            shouldHaveSomeFakeLogicToConsumeValue(holder, b10);
            shouldHaveSomeFakeLogicToConsumeValue(holder, c10);
            shouldHaveSomeFakeLogicToConsumeValue(holder, s10);
            shouldHaveSomeFakeLogicToConsumeValue(holder, i10);
            shouldHaveSomeFakeLogicToConsumeValue(holder, l10);
            shouldHaveSomeFakeLogicToConsumeValue(holder, f10);
            shouldHaveSomeFakeLogicToConsumeValue(holder, d10);
        }
    }
    
    private static final class SimpleArrayHolder {
        private byte[] bHolder;
        private char[] cHolder;
        private short[] sHolder;
        private int[] iHolder;
        private long[] lHolder;
        private float[] fHolder;
        private double[] dHolder;
        private Object[] oHolder;
        
        public SimpleArrayHolder(int bSize, int cSize, int sSize, int iSize, int lSize, int fSize, int dSize, int oSize) {
            bHolder = new byte[bSize];
            cHolder = new char[cSize];
            sHolder = new short[sSize];
            iHolder = new int[iSize];
            lHolder = new long[lSize];
            fHolder = new float[fSize];
            dHolder = new double[dSize];
            oHolder = new Object[oSize];
        }
        
        public byte getB(int idx) {
            return bHolder[idx];
        }
        public char getC(int idx) {
            return cHolder[idx];
        }
        public short getS(int idx) {
            return sHolder[idx];
        }
        public int getI(int idx) {
            return iHolder[idx];
        }
        public long getL(int idx) {
            return lHolder[idx];
        }
        public float getF(int idx) {
            return fHolder[idx];
        }
        public double getD(int idx) {
            return dHolder[idx];
        }
        public Object getO(int idx) {
            return oHolder[idx];
        }
        
        public void setB(int idx, byte v) {
            bHolder[idx] = v;
        }
        public void setC(int idx, char v) {
            cHolder[idx] = v;
        }
        public void setS(int idx, short v) {
            sHolder[idx] = v;
        }
        public void setI(int idx, int v) {
            iHolder[idx] = v;
        }
        public void setL(int idx, long v) {
            lHolder[idx] = v;
        }
        public void setF(int idx, float v) {
            fHolder[idx] = v;
        }
        public void setD(int idx, double v) {
            dHolder[idx] = v;
        }
        public void setO(int idx, Object v) {
            oHolder[idx] = v;
        }
    }
    
    
    







    
    private static boolean shouldHaveSomeFakeLogicToAlwaysReturnFalse(Object obj) {
        return obj.toString().contains("{}}{}{}{}"); // shuold always return false
    }

    private static void shouldHaveSomeFakeLogicToConsumeValue(Object obj, Object val) {
        if (obj.toString().contains("{}}{}{}{}")) { // should never go in to, should always be false
            System.out.println(val);
        }
    }
}
