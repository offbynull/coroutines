package com.offbynull.coroutines.antplugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class InstrumentTaskTest {
    
    private InstrumentTask fixture;
    
    @BeforeEach
    public void setUp() {
        fixture = new InstrumentTask();
    }

    @Test
    public void mustInstrumentClasses() throws Exception {
        byte[] inputContent = readZipFromResource("NormalInvokeTest.zip").get("NormalInvokeTest.class");
        
        File inputDir = null;
        File outputDir = null;
        try {
            // create folders
            inputDir = Files.createTempDirectory(getClass().getSimpleName()).toFile();
            outputDir = Files.createTempDirectory(getClass().getSimpleName()).toFile();
            
            // write out
            File inputClass = new File(inputDir, "NormalInvokeTest.class");
            FileUtils.writeByteArrayToFile(inputClass, inputContent);
            
            // setup
            fixture.setSourceDirectory(inputDir);
            fixture.setTargetDirectory(outputDir);
            fixture.setClasspath("");
            
            // execute plugin
            fixture.execute();
            
            // read back in
            File outputClass = new File(outputDir, "NormalInvokeTest.class");
            byte[] outputContent = FileUtils.readFileToByteArray(outputClass);
            
            // test
            assertTrue(outputContent.length > inputContent.length);
        } finally {
            if (inputDir != null) {
                FileUtils.deleteDirectory(inputDir);
            }
            
            if (outputDir != null) {
                FileUtils.deleteDirectory(outputDir);
            }
        }
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
