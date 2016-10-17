package com.offbynull.coroutines.instrumenter;

import com.offbynull.coroutines.instrumenter.generators.DebugGenerators.MarkerType;
import com.offbynull.coroutines.user.Coroutine;
import com.offbynull.coroutines.user.CoroutineRunner;
import org.junit.runner.RunWith;

@RunWith(ParallelRunner.class)
public class DebugModeInstrumenterTest extends BaseInstrumenterTest {

    @Override
    protected InstrumentationSettings generateInstrumentationSettings() {
        return new InstrumentationSettings(MarkerType.CONSTANT, true, false);
    }
    
    @Override
    protected CoroutineRunner generateCoroutineRunner(Coroutine coroutine) {
        return new CoroutineRunner(coroutine);
    }
}
