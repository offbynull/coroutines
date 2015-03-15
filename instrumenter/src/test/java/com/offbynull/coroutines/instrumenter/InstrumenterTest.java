package com.offbynull.coroutines.instrumenter;

import static com.offbynull.coroutines.instrumenter.TestUtils.loadClassesInZipResourceAndInstrument;
import com.offbynull.coroutines.user.Coroutine;
import java.net.URLClassLoader;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.Assert;
import org.junit.Test;

public final class InstrumenterTest {

    private static final String SIMPLETEST = "SimpleTest";


    @Test
    public void testSomeMethod() throws Exception {
        StringBuilder builder = new StringBuilder();

        try (URLClassLoader classLoader = loadClassesInZipResourceAndInstrument(SIMPLETEST + ".zip")) {
            Class<?> cls = classLoader.loadClass(SIMPLETEST);
            Object instance = ConstructorUtils.invokeConstructor(cls, builder);

            Coroutine coroutine = new Coroutine();

            MethodUtils.invokeMethod(instance, "run", coroutine.ready());
            MethodUtils.invokeMethod(instance, "run", coroutine.ready());
            MethodUtils.invokeMethod(instance, "run", coroutine.ready());
            MethodUtils.invokeMethod(instance, "run", coroutine.ready());
            MethodUtils.invokeMethod(instance, "run", coroutine.ready());
            MethodUtils.invokeMethod(instance, "run", coroutine.ready());
            MethodUtils.invokeMethod(instance, "run", coroutine.ready());
            MethodUtils.invokeMethod(instance, "run", coroutine.ready());
            MethodUtils.invokeMethod(instance, "run", coroutine.ready());
            MethodUtils.invokeMethod(instance, "run", coroutine.ready());
            MethodUtils.invokeMethod(instance, "run", coroutine.ready());
            MethodUtils.invokeMethod(instance, "run", coroutine.ready());
            MethodUtils.invokeMethod(instance, "run", coroutine.ready());
            MethodUtils.invokeMethod(instance, "run", coroutine.ready());

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

}
