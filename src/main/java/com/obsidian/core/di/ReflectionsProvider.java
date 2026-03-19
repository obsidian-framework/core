package com.obsidian.core.di;

import com.obsidian.core.core.Obsidian;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * Provides a single cached {@link Reflections} instance for the application.
 * Avoids repeated classpath scanning across loaders, scanners, and auto-detectors.
 */
public final class ReflectionsProvider
{
    /** Cached Reflections instance */
    private static volatile Reflections instance;

    private ReflectionsProvider() {}

    /**
     * Returns the cached Reflections instance.
     * Creates it on first call using {@link Obsidian#getBasePackage()}.
     *
     * @return Reflections instance configured with TypesAnnotated and SubTypes scanners
     * @throws IllegalStateException if base package is not set
     */
    public static Reflections get()
    {
        if (instance == null) {
            synchronized (ReflectionsProvider.class) {
                if (instance == null) {
                    String basePackage = Obsidian.getBasePackage();
                    if (basePackage == null) {
                        throw new IllegalStateException("Base package not set. Call Obsidian.setBasePackage() first.");
                    }
                    instance = new Reflections(
                            new ConfigurationBuilder().forPackage(basePackage).setScanners(Scanners.TypesAnnotated, Scanners.SubTypes)
                    );
                }
            }
        }
        return instance;
    }

    /**
     * Finds all classes annotated with the given annotation.
     *
     * @param annotation Annotation class to search for
     * @return Set of annotated classes
     */
    public static Set<Class<?>> getTypesAnnotatedWith(Class<? extends Annotation> annotation) {
        return get().getTypesAnnotatedWith(annotation);
    }

    /**
     * Finds all subtypes of the given class.
     *
     * @param type Parent class or interface
     * @param <T> Type parameter
     * @return Set of subtype classes
     */
    public static <T> Set<Class<? extends T>> getSubTypesOf(Class<T> type) {
        return get().getSubTypesOf(type);
    }

    /**
     * Clears the cached instance.
     * Useful for testing or when base package changes.
     */
    public static void clear() {
        instance = null;
    }
}