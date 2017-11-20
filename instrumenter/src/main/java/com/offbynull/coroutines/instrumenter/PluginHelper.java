/*
 * Copyright (c) 2017, Kasra Faghihi, All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.offbynull.coroutines.instrumenter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.Validate;

/**
 * Helper class for use by build system plugins.
 * @author Kasra Faghihi
 */
public final class PluginHelper {

    private PluginHelper() {
        // do nothing
    }

    /**
     * Given a source and destination path, scans the source path for class files and translates them to the destination path. This
     * method recursively scans the path.
     * <p>
     * For example, imagine source path of {@code /src} and a destination path of {@code /dst}...
     * <pre>
     * /src/A.class -&gt; /dst/A.class
     * /src/a/B.class -&gt; /dst/B.class
     * /src/a/b/c/d/e/C.class -&gt; /dst/a/b/c/d/e/C.class
     * /src/a/b/c/d/e/D.class -&gt; /dst/a/b/c/d/e/D.class
     * </pre>
     * @param srcDir source directory
     * @param dstDir destination directory
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if either of the paths passed in are not directories
     * @return source class to destination class mapping
     */
    public static Map<File, File> mapPaths(File srcDir, File dstDir) {
        Validate.notNull(srcDir);
        Validate.notNull(dstDir);
        Validate.isTrue(srcDir.isDirectory());
        Validate.isTrue(dstDir.isDirectory());

        Map<File, File> ret = new HashMap<>();
        
        FileUtils.listFiles(srcDir, new String[]{"class"}, true).forEach((inputFile) -> {
            Path relativePath = srcDir.toPath().relativize(inputFile.toPath());
            Path outputFilePath = dstDir.toPath().resolve(relativePath);
            File outputFile = outputFilePath.toFile();

            ret.put(inputFile, outputFile);
        });
        
        return ret;
    }

    /**
     * Instruments class files and generates detail files. Detail files are placed alongside destination class files -- they have the same
     * name but the extension will be changed to {@code .coroutinesinfo}.
     * @param instrumenter instrumenter
     * @param settings instrumentation settings
     * @param srcDstMapping class files to instrument mapped to destination files where the final instrumented results will be placed
     * @param logger logger to dump messages to (if any)
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if a source class file doesn't exist
     * @throws IOException on IO error
     */
    public static void instrument(Instrumenter instrumenter, InstrumentationSettings settings, Map<File, File> srcDstMapping,
            Consumer<String> logger) throws IOException {
        Validate.notNull(instrumenter);
        Validate.notNull(srcDstMapping);
        Validate.notNull(logger);

        for (Entry<File, File> e : srcDstMapping.entrySet()) {
            File inputFile = e.getKey();
            File outputFile = e.getValue();
            File outputDir = outputFile.getParentFile();

            Validate.notNull(inputFile);
            Validate.notNull(outputFile);
            Validate.isTrue(inputFile.isFile());
            // output file may not exists or it may exist (e.g. if we're writing out to the same location)

            byte[] input = FileUtils.readFileToByteArray(inputFile);
            
            InstrumentationResult result = instrumenter.instrument(input, settings);
            
            byte[] output = result.getInstrumentedClass();
            Map<String, byte[]> extraOutputs = result.getExtraFiles();
            
            if (input.length == output.length) { // condition that determines if no instrumentation happened
                continue;
            }

            FileUtils.writeByteArrayToFile(outputFile, output);
            for (Entry<String, byte[]> extraOutput : extraOutputs.entrySet()) {
                File extraFile = new File(outputDir, extraOutput.getKey());
                byte[] extraData = extraOutput.getValue();
                FileUtils.writeByteArrayToFile(extraFile, extraData);
            }
            
            logger.accept("Instrumenting " + inputFile.getAbsolutePath()
                    + " (" + input.length + " bytes -> " + output.length + " bytes)"
                    + (extraOutputs.isEmpty() ? "" : " with extra files " + extraOutputs.keySet()));
        }
    }

    /**
     * Instruments class files and generates detail files. This method is equivalent to calling...
     * <pre>
     * Map&lt;File, File&gt; srcDstMapping = mapPaths(srcDir, dstDir);
     * instrument(instrumenter, settings, srcDstMapping, logger);
     * </pre>
     * @param instrumenter instrumenter
     * @param settings instrumentation settings
     * @param srcDir source directory
     * @param dstDir destination directory
     * @param logger logger to dump messages to (if any)
     * @throws NullPointerException if any argument is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if either of the paths passed in are not directories (or if a file in {@code srcDir} was removed
     * while this method is executing)
     * @throws IOException on IO error
     */
    public static void instrument(Instrumenter instrumenter, InstrumentationSettings settings, File srcDir, File dstDir,
            Consumer<String> logger) throws IOException {
        Map<File, File> srcDstMapping = mapPaths(srcDir, dstDir);
        instrument(instrumenter, settings, srcDstMapping, logger);
    }
}
