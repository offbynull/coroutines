package com.offbynull.coroutines.instrumenter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerScheduler;

// http://stackoverflow.com/a/26234336
//
// Runs tests in parallel -- this is useful because intrumenter tests are taking a long time to execute. This cuts it down by over half.
public class ParallelRunner extends BlockJUnit4ClassRunner {

    public ParallelRunner(Class<?> klass) throws InitializationError {
        super(klass);
        setScheduler(new ParallelScheduler());
    }

    private static final class ParallelScheduler implements RunnerScheduler {

        private ExecutorService threadPool = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors());

        @Override
        public void schedule(Runnable childStatement) {
            threadPool.submit(childStatement);
        }

        @Override
        public void finished() {
            try {
                threadPool.shutdown();
                threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Got interrupted", e);
            }
        }
    }
}
