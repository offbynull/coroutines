package com.offbynull.coroutines.instrumenter;

import static com.offbynull.coroutines.instrumenter.TestUtils.loadClassesInZipResourceAndInstrument;
import com.offbynull.coroutines.user.Coroutine;
import com.offbynull.coroutines.user.CoroutineRunner;
import java.net.URLClassLoader;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class InstrumenterTest {

    private static final String NORMAL_INVOKE_TEST = "NormalInvokeTest";
    private static final String STATIC_INVOKE_TEST = "StaticInvokeTest";
    private static final String INTERFACE_INVOKE_TEST = "InterfaceInvokeTest";
    private static final String CONSTRUCTOR_INVOKE_TEST = "ConstructorInvokeTest";

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void shouldProperlySuspendWithVirtualMethods() throws Exception {
        StringBuilder builder = new StringBuilder();

        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(NORMAL_INVOKE_TEST + ".zip")) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(NORMAL_INVOKE_TEST);
            Coroutine coroutine = ConstructorUtils.invokeConstructor(cls, builder);

            CoroutineRunner runner = new CoroutineRunner(coroutine);

            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertFalse(runner.execute()); // coroutine finished executing here
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());

            Assert.assertEquals("started\n"
                    + "0\n"
                    + "1\n"
                    + "2\n"
                    + "3\n"
                    + "4\n"
                    + "5\n"
                    + "6\n"
                    + "7\n"
                    + "8\n"
                    + "9\n"
                    + "started\n"
                    + "0\n"
                    + "1\n"
                    + "2\n", builder.toString());
        }
    }
    
    @Test
    public void shouldProperlySuspendWithStaticMethods() throws Exception {
        StringBuilder builder = new StringBuilder();

        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(STATIC_INVOKE_TEST + ".zip")) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(STATIC_INVOKE_TEST);
            Coroutine coroutine = ConstructorUtils.invokeConstructor(cls, builder);

            CoroutineRunner runner = new CoroutineRunner(coroutine);

            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertFalse(runner.execute()); // coroutine finished executing here
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());

            Assert.assertEquals("started\n"
                    + "0\n"
                    + "1\n"
                    + "2\n"
                    + "3\n"
                    + "4\n"
                    + "5\n"
                    + "6\n"
                    + "7\n"
                    + "8\n"
                    + "9\n"
                    + "started\n"
                    + "0\n"
                    + "1\n"
                    + "2\n", builder.toString());
        }
    }

    @Test
    public void shouldProperlySuspendWithInterfaceMethods() throws Exception {
        StringBuilder builder = new StringBuilder();

        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(INTERFACE_INVOKE_TEST + ".zip")) {
            Class<Coroutine> cls = (Class<Coroutine>) classLoader.loadClass(INTERFACE_INVOKE_TEST);
            Coroutine coroutine = ConstructorUtils.invokeConstructor(cls, builder);

            CoroutineRunner runner = new CoroutineRunner(coroutine);

            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertFalse(runner.execute()); // coroutine finished executing here
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());
            Assert.assertTrue(runner.execute());

            Assert.assertEquals("started\n"
                    + "0\n"
                    + "1\n"
                    + "2\n"
                    + "3\n"
                    + "4\n"
                    + "5\n"
                    + "6\n"
                    + "7\n"
                    + "8\n"
                    + "9\n"
                    + "started\n"
                    + "0\n"
                    + "1\n"
                    + "2\n", builder.toString());
        }
    }
    
    @Test
    public void shouldNotInstrumentConstructors() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Instrumentation of constructors not allowed");

        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(CONSTRUCTOR_INVOKE_TEST + ".zip")) {
        }
    }
}
