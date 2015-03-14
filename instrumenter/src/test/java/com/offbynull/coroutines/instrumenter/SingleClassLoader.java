package com.offbynull.coroutines.instrumenter;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

final class SingleClassLoader extends URLClassLoader {


    public static SingleClassLoader create(ClassLoader parent, String className, byte[] classData) {
        SingleClassLoader classLoader = null;
        try {
            classLoader = new SingleClassLoader(parent);
            classLoader.defineClass(className, classData, 0, classData.length);
            return classLoader;
        } catch (RuntimeException re) {
            if (classLoader != null) {
                try {
                    classLoader.close();
                } catch (IOException ex) {
                    // do nothing
                }
            }
            throw re;
        }
    }
    
    private SingleClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
    }

}
