package com.offbynull.coroutines.instrumenter;

import com.offbynull.coroutines.instrumenter.generators.DebugGenerators.MarkerType;
import static com.offbynull.coroutines.instrumenter.testhelpers.TestUtils.loadClassesInZipResourceAndInstrument;
import com.offbynull.coroutines.user.Coroutine;
import com.offbynull.coroutines.user.CoroutineRunner;
import com.offbynull.coroutines.user.FrameAllocator;
import java.io.Serializable;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(ParallelRunner.class)
public class CustomAllocatorInstrumenterTest extends BaseInstrumenterTest {

    private CustomFrameAllocator frameAllocator;
    
    @Override
    protected InstrumentationSettings generateInstrumentationSettings() {
        return new InstrumentationSettings(MarkerType.CONSTANT, false, true);
    }
    
    @Override
    protected CoroutineRunner generateCoroutineRunner(Coroutine coroutine) {
        frameAllocator = new CustomFrameAllocator();
        return new CoroutineRunner(coroutine, frameAllocator);
    }

    @Override
    @Ignore("Test requires normal allocator, custom allocator won't work")
    public void mustNotFailWithVerifierErrorWhenRunningAsPerOfAnActor() throws Exception {
        // this test uses an inner coroutinerunner that doesn't use a custom allocator. as such it'll fail when you try to use it.
    }
    
    @Test
    public void mustProperlyReportExceptionsWithCustomAllocator() throws Exception {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception thrown during execution");

        try {        
            performCountTest(EXCEPTION_THROW_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, false));
        } catch (RuntimeException re) {
            assertEquals(TransactionCommand.ROLLBACK, frameAllocator.lastTransactionCommand);
            assertEquals(0, frameAllocator.commited.size());
            throw re;
        }
    }
    
    @Test
    public void mustProperlySuspendInTryCatchFinallyWithCustomAllocator() throws Exception {
        StringBuilder builder = new StringBuilder();

        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(EXCEPTION_SUSPEND_TEST + ".zip", settings)) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(EXCEPTION_SUSPEND_TEST);
            Coroutine coroutine = ConstructorUtils.invokeConstructor(cls, builder);

            CoroutineRunner runner = generateCoroutineRunner(coroutine);

            assertTrue(runner.execute());
            assertEquals(TransactionCommand.COMMIT, frameAllocator.lastTransactionCommand);
            System.out.println(frameAllocator.commited.size());
            assertTrue(runner.execute());
            assertEquals(TransactionCommand.COMMIT, frameAllocator.lastTransactionCommand);
            System.out.println(frameAllocator.commited.size());
            assertTrue(runner.execute());
            assertEquals(TransactionCommand.COMMIT, frameAllocator.lastTransactionCommand);
            System.out.println(frameAllocator.commited.size());
            assertFalse(runner.execute()); // coroutine finished executing here
            assertEquals(TransactionCommand.COMMIT, frameAllocator.lastTransactionCommand);
            assertEquals(0, frameAllocator.commited.size());
            System.out.println(frameAllocator.commited.size());

            assertEquals(
                    "START\n"
                    + "IN TRY 1\n"
                    + "IN TRY 2\n"
                    + "IN CATCH 1\n"
                    + "IN CATCH 2\n"
                    + "IN FINALLY 1\n"
                    + "IN FINALLY 2\n"
                    + "END\n", builder.toString());
        }
    }

    
    private static final class CustomFrameAllocator implements FrameAllocator, Serializable {

        private static final long serialVersionUID = 1L;
        
        private final Set<Object> commited = new HashSet<>();
        private final Set<Object> uncommitedAllocs = new HashSet<>();
        private final Set<Object> uncommitedReleases = new HashSet<>();
        
        private TransactionCommand lastTransactionCommand;

        @Override
        public int[] allocateIntArray(int size) {
            int[] ret = new int[size];
            uncommitedAllocs.add(ret);
            return ret;
        }

        @Override
        public long[] allocateLongArray(int size) {
            long[] ret = new long[size];
            uncommitedAllocs.add(ret);
            return ret;
        }

        @Override
        public float[] allocateFloatArray(int size) {
            float[] ret = new float[size];
            uncommitedAllocs.add(ret);
            return ret;
        }

        @Override
        public double[] allocateDoubleArray(int size) {
            double[] ret = new double[size];
            uncommitedAllocs.add(ret);
            return ret;
        }

        @Override
        public Object[] allocateObjectArray(int size) {
            Object[] ret = new Object[size];
            uncommitedAllocs.add(ret);
            return ret;
        }

        @Override
        public void releaseIntArray(int[] arr) {
            uncommitedReleases.add(arr);
        }

        @Override
        public void releaseLongArray(long[] arr) {
            uncommitedReleases.add(arr);
        }

        @Override
        public void releaseFloatArray(float[] arr) {
            uncommitedReleases.add(arr);
        }

        @Override
        public void releaseDoubleArray(double[] arr) {
            uncommitedReleases.add(arr);
        }

        @Override
        public void releaseObjectArray(Object[] arr) {
            uncommitedReleases.add(arr);
        }

        @Override
        public void rollback() {
            uncommitedAllocs.clear();
            uncommitedReleases.clear();
            lastTransactionCommand = TransactionCommand.ROLLBACK;
        }

        @Override
        public void commit() {
            commited.addAll(uncommitedAllocs);
            commited.removeAll(uncommitedReleases);
            lastTransactionCommand = TransactionCommand.COMMIT;
        }
        
    }
    
    private enum TransactionCommand {
        ROLLBACK,
        COMMIT
    }
}
