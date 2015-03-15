package com.offbynull.coroutines.instrumenter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

/**
 * Test utilities.
 *
 * @author Kasra Faghihi
 */
public final class TestUtils {

    private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";
    private static final String MANIFEST_TEXT = "Manifest-Version: 1.0\r\nCreated-By: " + TestUtils.class.getName();

    private TestUtils() {
        // do nothing
    }
    
    /**
     * Loads up a resource from the classpath as a {@link ClassNode}. Behaviour is unknown if class is not a parsable Java class file.
     * @param path path of resource
     * @return {@link ClassNode} representation of resource
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     * @throws IOException if any IO error occurs
     * @throws IllegalArgumentException if {@code path} cannot be found
     */
    public static ClassNode readResourceAsClassNode(String path) throws IOException {
        Validate.notNull(path);
        
        byte[] stubContents = TestUtils.getResource(path);
        ClassReader cr = new ClassReader(new ByteArrayInputStream(stubContents));
        ClassNode classNode = new ClassNode();
        cr.accept(classNode, 0);
        
        return classNode;
    }

    /**
     * Writes {@link ClassNode}s to a JAR. Behaviour is unknown if class is not valid in some way.
     * @param classNodes class nodes to put in to jar
     * @return {@link ClassNode} representation of resource
     * @throws IOException if any IO error occurs
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if {@code classNodes} is empty
     */
    public static URLClassLoader writeClassNodesToJarAndLoad(ClassNode ... classNodes) throws IOException {
        Validate.notNull(classNodes);
        Validate.noNullElements(classNodes);
        Validate.isTrue(classNodes.length >= 0);
        
        JarEntry[] jarEntries = new JarEntry[classNodes.length];
        for (int i = 0; i < jarEntries.length; i++) {
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            classNodes[i].accept(cw);

            jarEntries[i] = new JarEntry(classNodes[i].name + ".class", cw.toByteArray());
        }
        
        File jarFile = createJar(jarEntries);
        return URLClassLoader.newInstance(new URL[] { jarFile.toURI().toURL() }, TestUtils.class.getClassLoader());
    }
    
    /**
     * Loads up a resource from the classpath as a byte array.
     * @param path path of resource
     * @return contents of resource
     * @throws IOException if any IO error occurs
     * @throws IllegalArgumentException if {@code path} cannot be found
     */
    public static byte[] getResource(String path) throws IOException {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        URL url = cl.getResource(path);
        Validate.isTrue(url != null);
        try (InputStream in = url.openStream()) {
            return IOUtils.toByteArray(in);
        }
    }

    /**
     * Gets the system classpath and creates a list of {@link File} objects for each path.
     *
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
     *
     * @param entries files to put inside of the jar
     * @return resulting jar file
     * @throws IOException if any IO errors occur
     * @throws NullPointerException if any argument is {@code null} or contains {@code null} elements
     */
    public static File createJar(JarEntry... entries) throws IOException {
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
         *
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
