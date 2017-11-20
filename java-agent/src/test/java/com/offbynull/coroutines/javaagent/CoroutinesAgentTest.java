package com.offbynull.coroutines.javaagent;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CoroutinesAgentTest {
    
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void mustInstrumentClasses() throws Exception {
        Instrumentation inst = mock(Instrumentation.class);
        String agentArgs = null;
        
        CoroutinesAgent.premain(agentArgs, inst);
        
        ArgumentCaptor<ClassFileTransformer> captor = ArgumentCaptor.forClass(ClassFileTransformer.class);
        verify(inst).addTransformer(captor.capture());
        
        byte[] inputContent = readZipFromResource("NormalInvokeTest.zip").get("NormalInvokeTest.class");
        
        ClassFileTransformer tranformer = captor.getValue();
        byte[] outputContent = tranformer.transform(
                getClass().getClassLoader(),
                "NormalInvokeTest",
                null,
                null,
                inputContent);
        
        assertTrue(outputContent.length > inputContent.length);
    }

    @Test
    public void mustInstrumentClassesWithParams() throws Exception {
        Instrumentation inst = mock(Instrumentation.class);
        String agentArgs = "markerType=STDOUT,debugMode=true,autoSerializable=false";
        
        CoroutinesAgent.premain(agentArgs, inst);
        
        ArgumentCaptor<ClassFileTransformer> captor = ArgumentCaptor.forClass(ClassFileTransformer.class);
        verify(inst).addTransformer(captor.capture());
        
        byte[] inputContent = readZipFromResource("NormalInvokeTest.zip").get("NormalInvokeTest.class");
        
        ClassFileTransformer tranformer = captor.getValue();
        byte[] outputContent = tranformer.transform(
                getClass().getClassLoader(),
                "NormalInvokeTest",
                null,
                null,
                inputContent);
        
        assertTrue(outputContent.length > inputContent.length);
    }

    @Test
    public void mustFailIfDebugTypeIncorrect() throws Exception {
        Instrumentation inst = mock(Instrumentation.class);
        String agentArgs = "markerType=NONE,debugType=ffffffffffff";
        
        expectedException.expect(IllegalArgumentException.class);
        CoroutinesAgent.premain(agentArgs, inst);
    }

    @Test
    public void mustFailIfMarkerTypeIncorrect() throws Exception {
        Instrumentation inst = mock(Instrumentation.class);
        String agentArgs = "markerType=fffffffffff,debugMode=false";
        
        expectedException.expect(IllegalArgumentException.class);
        CoroutinesAgent.premain(agentArgs, inst);
    }

    @Test
    public void mustFailIfNoEqualsInArg() throws Exception {
        Instrumentation inst = mock(Instrumentation.class);
        String agentArgs = "NONE,debugMode=false";
        
        expectedException.expect(IllegalArgumentException.class);
        CoroutinesAgent.premain(agentArgs, inst);
    }

    @Test
    public void mustFailIfUnknownArg() throws Exception {
        Instrumentation inst = mock(Instrumentation.class);
        String agentArgs = "pewpewpew=false";
        
        expectedException.expect(IllegalArgumentException.class);
        CoroutinesAgent.premain(agentArgs, inst);
    }
    
    private Map<String, byte[]> readZipFromResource(String path) throws IOException {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        URL url = cl.getResource(path);
        Validate.isTrue(url != null);
        
        Map<String, byte[]> ret = new LinkedHashMap<>();
        
        try (InputStream is = url.openStream();
                ZipArchiveInputStream zais = new ZipArchiveInputStream(is)) {
            ZipArchiveEntry entry;
            while ((entry = zais.getNextZipEntry()) != null) {
                ret.put(entry.getName(), IOUtils.toByteArray(zais));
            }
        }
        
        return ret;
    }
}
