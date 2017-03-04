/*
 * Copyright (c) 2016, Kasra Faghihi, All rights reserved.
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
package com.offbynull.coroutines.javaagent;

import com.offbynull.coroutines.instrumenter.InstrumentationSettings;
import com.offbynull.coroutines.instrumenter.Instrumenter;
import com.offbynull.coroutines.instrumenter.asm.ClassLoaderClassInformationRepository;
import com.offbynull.coroutines.instrumenter.asm.SimpleClassNode;
import com.offbynull.coroutines.instrumenter.generators.DebugGenerators.MarkerType;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Arrays;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/**
 * Java Agent that instruments coroutines.
 * @author Kasra Faghihi
 */
public final class CoroutinesAgent {

    private CoroutinesAgent() {
        // do nothing
    }
    
    /**
     * Java agent premain.
     * @param agentArgs args passed in to agent
     * @param inst instrumentation for agent
     * @throws NullPointerException if {@code inst} is {@code null}
     * @throws IllegalArgumentException if {@code agentArgs} is present but not in the format {@code markerType,debugMode}, or if the passed
     * in arguments were not parseable (debugMode must be a boolean and markerType must be a member of {@link MarkerType})
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        // How do agent args work? http://stackoverflow.com/questions/23287228/how-do-i-pass-arguments-to-a-java-instrumentation-agent
        // e.g. java -javaagent:/path/to/agent.jar=argumentstring
        
        MarkerType markerType = MarkerType.NONE;
        boolean debugMode = false;
        if (agentArgs != null && !agentArgs.isEmpty()) {
            String[] splitArgs = agentArgs.split(",");
            if (splitArgs.length != 2) {
                throw new IllegalArgumentException("Expected argument format is: markerType,debugMode");
            }
            
            try {
                markerType = MarkerType.valueOf(splitArgs[0]);
            } catch (IllegalArgumentException iae) {
                throw new IllegalArgumentException("Unable to parse marker type -- must be one of the following: "
                        + Arrays.toString(MarkerType.values()), iae);
            }
            
            if (splitArgs[1].equalsIgnoreCase("true")) {
                debugMode = true;
            } else if (splitArgs[1].equalsIgnoreCase("false")) {
                debugMode = false;
            } else {
                throw new IllegalArgumentException("Unable to parse debug mode -- must be true or false");
            }
            
        }
        
        inst.addTransformer(new CoroutinesClassFileTransformer(markerType, debugMode));
    }
    
    private static final class CoroutinesClassFileTransformer implements ClassFileTransformer {
        private final MarkerType markerType;
        private final boolean debugMode;

        CoroutinesClassFileTransformer(MarkerType markerType, boolean debugMode) {
            if (markerType == null) {
                throw new NullPointerException();
            }

            this.markerType = markerType;
            this.debugMode = debugMode;
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                byte[] classfileBuffer) throws IllegalClassFormatException {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassNode classNode = new SimpleClassNode();
            cr.accept(classNode, 0);
            String classNameFromBytes = classNode.name;
            
            // If class is internal to the coroutines user project, don't instrument them
            //   FYI: If the class being transformed is a lambda, className will show up as null.
            if (classNameFromBytes.startsWith("com/offbynull/coroutines/user/")) {
                return null;
            }
            
            // If loader is null, don't attempt instrumentation (this is a core class?)
            if (loader == null) {
                return null;
            }
            
            System.out.println(classNameFromBytes + " " + (loader == null));

//            if (classNameFromBytes.equals("TestFiber2")) {
//                try {
//                    System.out.println(Class.forName("TestFiber2$TestFiber3", false, loader));
//                } catch (ClassNotFoundException ex) {
//                    System.out.println("failed");
//                }
//            }
//            System.out.println(cir.getInformation("TestFiber2"));
//            System.out.println(cir.getInformation("TestFiber2.TestFiber3"));
//            System.out.println(cir.getInformation("TestFiber2$TestFiber3"));
//            System.out.println(cir.getInformation("TestFiber3"));
//            
//            if (1 == 1) {
//                return classfileBuffer;
//            }
            
            try {
                Instrumenter instrumenter = new Instrumenter(new ClassLoaderClassInformationRepository(loader));
                byte[] moddedClassfileBuffer = instrumenter.instrument(
                        classfileBuffer,
                        new InstrumentationSettings(markerType, debugMode));
                return moddedClassfileBuffer;
            } catch (Throwable e) {
                System.err.println("FAILED TO INSTRUMENT: " + e);
                return null;
            }
        }
        
    }
}
