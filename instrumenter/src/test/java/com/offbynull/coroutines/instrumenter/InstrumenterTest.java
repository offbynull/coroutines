package com.offbynull.coroutines.instrumenter;

import com.offbynull.coroutines.instrumenter.TestUtils.JarEntry;
import static com.offbynull.coroutines.instrumenter.TestUtils.createJar;
import static com.offbynull.coroutines.instrumenter.TestUtils.getClasspath;
import static com.offbynull.coroutines.instrumenter.TestUtils.getResource;
import com.offbynull.coroutines.user.Coroutine;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class InstrumenterTest {

    private URLClassLoader classLoader;

    @Before
    public void setUp() throws Exception {
        // Create jar of original class
        byte[] originalClass = getResource("SimpleTest_ORIG.class");
        File originalJarFile = createJar(new JarEntry("SimpleTest.class", originalClass));
        
        // Construct classpath required for instrumentation
        List<File> instrumentationClasspath = getClasspath();
        instrumentationClasspath.add(originalJarFile);
        
        // Instrument class
        byte[] instrumentedClass = new Instrumenter(instrumentationClasspath).instrument(originalClass);
//        FileUtils.writeByteArrayToFile(new File("out.class"), instrumentedClass); // temp
        
        // Create jar of instrumented class and set for use in classloader
        File instrumentedJarFile = createJar(new JarEntry("SimpleTest.class", instrumentedClass));
        classLoader = URLClassLoader.newInstance(new URL[] { instrumentedJarFile.toURI().toURL() }, getClass().getClassLoader());
    }

    @After
    public void tearDown() throws IOException {
        if (classLoader != null) {
            classLoader.close();
        }
    }

    @Test
    public void testSomeMethod() throws Exception {
        Class<?> cls = classLoader.loadClass("SimpleTest");
        Object instance = cls.newInstance();
        
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
    }

}
