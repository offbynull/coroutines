package com.offbynull.coroutines.instrumenter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.Charsets;
import org.apache.commons.lang3.Validate;

/**
 * Test utilities.
 * @author Kasra Faghihi
 */
public final class TestUtils {

    private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";
    private static final String MANIFEST_TEXT = "Manifest-Version: 1.0\r\nCreated-By: " + TestUtils.class.getName();
    
    private TestUtils() {
        // do nothing
    }
    
    /**
     * Gets the system classpath and creates a list of {@link File} objects for each path.
     * @return classpath as list of {@link File} objects
     * @throws IllegalStateException if the classpath doesn't exist or can't be split up
     */
    public static List<File> getClasspath() {
        String classpath = System.getProperty("java.class.path");
        String pathSeparator = System.getProperty("path.separator");
        Validate.validState(classpath != null && pathSeparator != null);
        List<File> classPathFiles = Arrays
                .stream(classpath.split(Pattern.quote(pathSeparator)))
                .map(x -> new File(x))
                .collect(Collectors.toList());
        
        return new ArrayList<>(classPathFiles);
    }

    /**
     * Creates a JAR as a temporary file. Simple manifest file will automatically be inserted.
     * @param entries files to put inside of the jar
     * @return resulting jar file
     * @throws IOException if any IO errors occur
     * @throws NullPointerException if any argument is {@code null} or contains {@code null} elements
     */
    public static File createJar(JarEntry ... entries) throws IOException {
        Validate.notNull(entries);
        Validate.noNullElements(entries);
        
        File tempFile = File.createTempFile(TestUtils.class.getSimpleName(), ".jar");
        tempFile.deleteOnExit();
        
        try (ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(new FileOutputStream(tempFile))) {
            writeJarEntry(zaos, MANIFEST_PATH, MANIFEST_TEXT.getBytes(Charsets.UTF_8));
            for (JarEntry entry : entries) {
                writeJarEntry(zaos, entry.name, entry.data);
            }
        }
        
        return tempFile;
    }

    private static void writeJarEntry(ZipArchiveOutputStream zout, String name, byte[] data) throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        entry.setSize(data.length);
        zout.putArchiveEntry(entry);
        zout.write(data);
        zout.closeArchiveEntry();
    }
    
    /**
     * An entry for a JAR file.
     */
    public static final class JarEntry {
        private final String name;
        private final byte[] data;

        /**
         * Constructs a {@link JarEntry} object.
         * @param name path of the file within the jar
         * @param data contents of the jar
         * @throws NullPointerException if any argument is {@code null}
         * @throws IllegalArgumentException if {@code name} is empty
         */
        public JarEntry(String name, byte[] data) {
            Validate.notNull(name);
            Validate.notNull(data);
            Validate.isTrue(!name.isEmpty());
            this.name = name;
            this.data = data.clone();
        }
    }
}
