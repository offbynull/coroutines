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
package com.offbynull.coroutines.instrumenter.asm;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import org.apache.commons.lang3.Validate;

/**
 * Provides information on classes contained within a {@link ClassLoader}. The difference between this class and
 * {@link ClassLoaderClassInformationRepository} is that this class loads the class as a resource (byte array) instead of actually loading
 * the class in to the JVM. Parsing of the resource is done the same way as {@link FileSystemClassInformationRepository}.
 * <p>
 * This is an attempt to work around the issue with loading classes and {@link ClassFileTransformer} (something we need to do for resolving
 * stack map frames after instrumenting the class). If
 * {@link ClassFileTransformer#transform(java.lang.ClassLoader, java.lang.String, java.lang.Class, java.security.ProtectionDomain, byte[])}
 * calls {@link Class#forName(java.lang.String) } for a class that hasn't been passed to that transformer yet, that class will never get
 * passed to that transformer.
 * <p>
 * As a work-around, we load the class bytes and parse that rather than loading the actual class.
 * @author Kasra Faghihi
 * @see ClassLoaderClassInformationRepository
 */
public final class ClassResourceClassInformationRepository implements ClassInformationRepository {
    private final ClassLoader classLoader;

    /**
     * Constructs a {@link ClassLoaderClassInformationRepository} object.
     * @param classLoader classloader to extract information from
     * @throws NullPointerException if any argument is {@code null}
     */
    public ClassResourceClassInformationRepository(ClassLoader classLoader) {
        Validate.notNull(classLoader);
        this.classLoader = classLoader;
    }

    //Hi !
    //
    //----- Mail original -----
    //> De: "offbynull-asm" <offbynull-asm@offbynull.com>
    //> À: asm@ow2.org
    //> Envoyé: Samedi 4 Mars 2017 23:23:26
    //> Objet: [asm] ClassFileTransformer and ClassWriter
    //
    //> I noticed that if ClassFileTransformer.transform() calls Class.forName()
    //> for a class that hasn't been passed to that transformer yet, that class
    //> will never get passed to that transformer.
    //>
    //> It looks like ClassWriter uses Class.forName() when it computes frames.
    //> How is it that people can use ClassWriter within a ClassFileTransformer
    //> without running in to this problem? Is there special logic somewhere in
    //> ASM that handles this?
    //
    //If you want to write Java 1.6 compatible code, you have to generate stackmap frames,
    //ASM can do that for you but it needs to know how to find the common supertype of two types,
    //this is needed by example when you merge variables that comes from the two branches of an if
    //(if you know the SSA form, you need that everywhere you have a phi).
    //
    //ASM provides a default implementation, ClassWriter.getCommonSuperType() that relies on Class.forName,
    //which as you said is not something you should do if you are inside a ClassFileTransformer
    //(which is not re-entrant).
    //
    //To cope with that issue, you can load the classfile using classloader.getResources(), use ASM to find the supertypes and compute the
    //common super types.
    //
    //But i suppose that you already know all of this, (you are the author of coroutines, right ?) so to answer to your question, no, ASM
    //does not provide any code for dealing with that, you have to write your own.
    //
    //The other solution, which i think in your case is the right solution, is to not ask ASM to generate the stackmap for you, but read the
    //existing stackmap and patch them (in MethodVisitor.vuisitFrame), it's painful, but in your case, it's the only way i see to do your
    //transformations properly.
    //
    //ASM can call visitFrame with two forms:
    //  - the compressed form which is how a frame is encoded in the bytecode (the stackframe may be defined as the delta from the last
    //    stackframe in the bytecode), this form is not suitable for patching 
    //  - the expanded form, which get you the types of the local variables + stack which is easier to patch.
    //To get the expanded form, you have to pass EXPAND_FRAMES to the ClassReader.
    //
    //regards,
    //Rémi
    
    // NOTE: If this doesn't work, try to do what Remi said with regards to patching the existing stackmap frames.
    
    @Override
    public ClassInformation getInformation(String internalClassName) {
        Validate.notNull(internalClassName);
        
        try (InputStream is = classLoader.getResourceAsStream(internalClassName + ".class")) {
            if (is == null) {
                return null;
            }
            
            return InternalUtils.getClassInformation(is);
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe); // this should never happen
        }
    }
}
