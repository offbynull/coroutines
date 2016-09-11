package com.offbynull.coroutines.instrumenter.benchmarks;

import java.util.ArrayList;
import java.util.List;

public class ObjectArrayVsHolderBenchmark {

    private static final int ROUNDS = 30000000;

    // HERE ARE THE RESULTS OF THIS TEST ON JAVA8 HOME PC
    // Holder:[65933, 66673, 64629, 65141, 67052]
    // Array:[50743, 50949, 50067, 51036, 49849]
    // Holder via set:[68385, 66903, 65026, 68711, 67211]
    public static void main(String[] args) {
        long startTime;
        long endTime;

        List<Long> diffTimes1 = new ArrayList<>();
        List<Long> diffTimes2 = new ArrayList<>();
        List<Long> diffTimes3 = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            startTime = System.currentTimeMillis();
            testHolder();
            endTime = System.currentTimeMillis();
            diffTimes1.add(endTime - startTime);

            startTime = System.currentTimeMillis();
            testObjectArray();
            endTime = System.currentTimeMillis();
            diffTimes2.add(endTime - startTime);

            startTime = System.currentTimeMillis();
            testHolderViaSet();
            endTime = System.currentTimeMillis();
            diffTimes3.add(endTime - startTime);
        }

        System.out.println("Holder:" + diffTimes1);
        System.out.println("Array:" + diffTimes2);
        System.out.println("Holder via set:" + diffTimes3);
    }

    private static void testObjectArray() {
        for (int num = 0; num < ROUNDS; num++) {
            Object[] h = new Object[5];
            SingleHolder singleHolder = new SingleHolder();
            h[0] = new int[10];
            h[1] = new long[10];
            h[2] = new float[10];
            h[3] = new double[10];
            h[4] = new Object[10];
            
            singleHolder.oHolder = h;
            shouldHaveSomeFakeLogicToConsumeValue(singleHolder, h);
            h = singleHolder.oHolder;
            
            int[] iHolder = (int[]) h[0];
            long[] lHolder = (long[]) h[1];
            float[] fHolder = (float[]) h[2];
            double[] dHolder = (double[]) h[3];
            Object[] oHolder = (Object[]) h[4];
            
            shouldHaveSomeFakeLogicToConsumeValue(h, iHolder);
            shouldHaveSomeFakeLogicToConsumeValue(h, lHolder);
            shouldHaveSomeFakeLogicToConsumeValue(h, fHolder);
            shouldHaveSomeFakeLogicToConsumeValue(h, dHolder);
            shouldHaveSomeFakeLogicToConsumeValue(h, oHolder);
        }
    }

    private static void testHolder() {
        for (int num = 0; num < ROUNDS; num++) {
            Holder h = new Holder();
            h.iHolder = new int[10];
            h.lHolder = new long[10];
            h.fHolder = new float[10];
            h.dHolder = new double[10];
            h.oHolder = new Object[10];
            
            shouldHaveSomeFakeLogicToConsumeValue(h, h);
            
            int[] iHolder = h.iHolder;
            long[] lHolder = h.lHolder;
            float[] fHolder = h.fHolder;
            double[] dHolder = h.dHolder;
            Object[] oHolder = h.oHolder;
            
            shouldHaveSomeFakeLogicToConsumeValue(h, iHolder);
            shouldHaveSomeFakeLogicToConsumeValue(h, lHolder);
            shouldHaveSomeFakeLogicToConsumeValue(h, fHolder);
            shouldHaveSomeFakeLogicToConsumeValue(h, dHolder);
            shouldHaveSomeFakeLogicToConsumeValue(h, oHolder);
        }
    }

    private static void testHolderViaSet() {
        for (int num = 0; num < ROUNDS; num++) {
            Holder h = new Holder();
            int[] iHolder = new int[10];
            long[] lHolder = new long[10];
            float[] fHolder = new float[10];
            double[] dHolder = new double[10];
            Object[] oHolder = new Object[10];

            h.set(iHolder, lHolder, fHolder, dHolder, oHolder);
            
            shouldHaveSomeFakeLogicToConsumeValue(h, h);
            
            int[] iHolder2 = h.iHolder;
            long[] lHolder2 = h.lHolder;
            float[] fHolder2 = h.fHolder;
            double[] dHolder2 = h.dHolder;
            Object[] oHolder2 = h.oHolder;
            
            shouldHaveSomeFakeLogicToConsumeValue(h, iHolder2);
            shouldHaveSomeFakeLogicToConsumeValue(h, lHolder2);
            shouldHaveSomeFakeLogicToConsumeValue(h, fHolder2);
            shouldHaveSomeFakeLogicToConsumeValue(h, dHolder2);
            shouldHaveSomeFakeLogicToConsumeValue(h, oHolder2);
        }
    }

    private static final class Holder {

        public int[] iHolder;
        public long[] lHolder;
        public float[] fHolder;
        public double[] dHolder;
        public Object[] oHolder;

        public void set(int[] iHolder, long[] lHolder, float[] fHolder, double[] dHolder, Object[] oHolder) {
            this.iHolder = iHolder;
            this.lHolder = lHolder;
            this.fHolder = fHolder;
            this.dHolder = dHolder;
            this.oHolder = oHolder;
        }
        
    }

    private static final class SingleHolder {

        public Object[] oHolder;
        
    }

    private static void shouldHaveSomeFakeLogicToConsumeValue(Object obj, Object val) {
        if (obj.toString().contains("{}}{}{}{}")) { // should never go in to, should always be false
            System.out.println(val);
        }
    }
}
