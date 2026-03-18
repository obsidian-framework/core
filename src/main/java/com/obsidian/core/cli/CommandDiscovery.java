package com.obsidian.core.cli;

import com.obsidian.core.cli.annotations.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;

/**
 * Scans the classpath for classes annotated with {@link Command}.
 * Supports compiled classes (target/classes) and JARs.
 */
public class CommandDiscovery
{
    private static final Logger logger = LoggerFactory.getLogger(CommandDiscovery.class);

    /**
     * @return unmodifiable list of {@link Command}-annotated classes found on the classpath
     */
    public static List<Class<?>> discover()
    {
        List<Class<?>> found = new ArrayList<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        for (URL url : classpathUrls(cl)) {
            try {
                File f = new File(url.toURI());
                if      (f.isDirectory())             scanDir(f, f, cl, found);
                else if (f.getName().endsWith(".jar")) scanJar(f, cl, found);
            } catch (Exception e) {
                logger.debug("Skipping classpath entry {}: {}", url, e.getMessage());
            }
        }

        return Collections.unmodifiableList(found);
    }

    /**
     * @param cl the classloader to introspect
     * @return list of URLs representing every classpath entry
     */
    private static List<URL> classpathUrls(ClassLoader cl)
    {
        List<URL> urls = new ArrayList<>();
        if (cl instanceof URLClassLoader ucl) {
            urls.addAll(Arrays.asList(ucl.getURLs()));
        } else {
            for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
                try { urls.add(new File(entry).toURI().toURL()); }
                catch (MalformedURLException e) {
                    logger.debug("Skipping malformed classpath entry '{}': {}", entry, e.getMessage());
                }
            }
        }
        return urls;
    }

    /**
     * @param root  classpath root directory
     * @param dir   current directory being scanned
     * @param cl    classloader used to load candidate classes
     * @param found accumulator for {@link Command}-annotated classes
     */
    private static void scanDir(File root, File dir, ClassLoader cl, List<Class<?>> found)
    {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanDir(root, f, cl, found);
            } else if (f.getName().endsWith(".class")) {
                String name = root.toURI().relativize(f.toURI()).getPath()
                        .replace('/', '.').replace(".class", "");
                tryLoad(name, cl, found);
            }
        }
    }

    /**
     * @param jar   JAR file to scan
     * @param cl    classloader used to load candidate classes
     * @param found accumulator for {@link Command}-annotated classes
     * @throws IOException if the JAR cannot be opened or read
     */
    private static void scanJar(File jar, ClassLoader cl, List<Class<?>> found) throws IOException
    {
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".class") && !name.contains("$")) {
                    tryLoad(name.replace('/', '.').replace(".class", ""), cl, found);
                }
            }
        }
    }

    /**
     * @param className fully-qualified binary class name
     * @param cl        classloader to use
     * @param found     accumulator for matching classes
     */
    private static void tryLoad(String className, ClassLoader cl, List<Class<?>> found)
    {
        try {
            Class<?> cls = cl.loadClass(className);
            if (cls.isAnnotationPresent(Command.class)) found.add(cls);
        } catch (Throwable e) {
            logger.trace("Cannot load class '{}': {}", className, e.getMessage());
        }
    }
}