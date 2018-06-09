package com.offbynull.coroutines.mavenplugin;

import com.offbynull.coroutines.instrumenter.generators.DebugGenerators.MarkerType;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public final class MainInstrumentMojoTest {
    
    private MavenProject mavenProject;
    
    private MainInstrumentMojo fixture;
    
    @BeforeEach
    public void setUp() throws Exception {
        fixture = new MainInstrumentMojo();
        
        mavenProject = Mockito.mock(MavenProject.class);
        Log log = Mockito.mock(Log.class);
        
        FieldUtils.writeField(fixture, "project", mavenProject, true);
        FieldUtils.writeField(fixture, "markerType", MarkerType.NONE, true);
        FieldUtils.writeField(fixture, "debugMode", false, true);
        FieldUtils.writeField(fixture, "log", log, true);
    }

    @Test
    public void mustInstrumentClasses() throws Exception {
        byte[] classContent = readZipFromResource("NormalInvokeTest.zip").get("NormalInvokeTest.class");
        
        File mainDir = null;
        try {
            // write out
            mainDir = Files.createTempDirectory(getClass().getSimpleName()).toFile();
            File mainClass = new File(mainDir, "NormalInvokeTest.class");
            FileUtils.writeByteArrayToFile(mainClass, classContent);
            
            // mock
            Mockito.when(mavenProject.getCompileClasspathElements()).thenReturn(Collections.emptyList());
            Build build = Mockito.mock(Build.class);
            Mockito.when(mavenProject.getBuild()).thenReturn(build);
            Mockito.when(build.getOutputDirectory()).thenReturn(mainDir.getAbsolutePath());
            
            // execute plugin
            fixture.execute();
            
            // read back in
            byte[] modifiedMainClassContent = FileUtils.readFileToByteArray(mainClass);
            
            // test
            assertTrue(modifiedMainClassContent.length > classContent.length);
        } finally {
            if (mainDir != null) {
                FileUtils.deleteDirectory(mainDir);
            }
        }
    }

    @Test
    public void mustNotThrowExceptionWhenDirectoryDoesntExist() throws Exception {
        File mainDir = null;
        try {
            // create a folder and delete it right away
            mainDir = Files.createTempDirectory(getClass().getSimpleName()).toFile();
            File fakeFolder = new File(mainDir, "DOESNOTEXIST");
            
            // mock
            Mockito.when(mavenProject.getCompileClasspathElements()).thenReturn(Collections.emptyList());
            Build build = Mockito.mock(Build.class);
            Mockito.when(mavenProject.getBuild()).thenReturn(build);
            Mockito.when(build.getOutputDirectory()).thenReturn(fakeFolder.getAbsolutePath());
            
            // execute plugin
            fixture.execute();
        } finally {
            if (mainDir != null) {
                FileUtils.deleteDirectory(mainDir);
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
