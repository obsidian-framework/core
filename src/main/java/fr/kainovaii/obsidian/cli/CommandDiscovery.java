package fr.kainovaii.obsidian.cli;

import fr.kainovaii.obsidian.cli.annotations.Command;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;

/**
 * Scans the classpath for classes annotated with {@link Command}.
 * Supports compiled classes (target/classes) and JARs.
 */
public class CommandDiscovery {


    public static List<Class<?>> discover()
    {
        List<Class<?>> found = new ArrayList<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        for (URL url : classpathUrls(cl)) {
            try {
                File f = new File(url.toURI());
                if      (f.isDirectory())             scanDir(f, f, cl, found);
                else if (f.getName().endsWith(".jar")) scanJar(f, cl, found);
            } catch (Exception ignored) {}
        }

        return Collections.unmodifiableList(found);
    }

    private static List<URL> classpathUrls(ClassLoader cl)
    {
        List<URL> urls = new ArrayList<>();
        if (cl instanceof URLClassLoader ucl) {
            urls.addAll(Arrays.asList(ucl.getURLs()));
        } else {
            for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
                try { urls.add(new File(entry).toURI().toURL()); }
                catch (MalformedURLException ignored) {}
            }
        }
        return urls;
    }

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

    private static void tryLoad(String className, ClassLoader cl, List<Class<?>> found)
    {
        try {
            Class<?> cls = cl.loadClass(className);
            if (cls.isAnnotationPresent(Command.class)) found.add(cls);
        } catch (Throwable ignored) {}
    }
}
