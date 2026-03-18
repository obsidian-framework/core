package com.obsidian.core.di;

import com.obsidian.core.core.Obsidian;
import com.obsidian.core.di.annotations.Repository;
import com.obsidian.core.di.annotations.Service;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * Component scanner for dependency injection.
 * Discovers and registers @Repository and @Service annotated classes.
 */
public class ComponentScanner
{
    private static final Logger logger = LoggerFactory.getLogger(ComponentScanner.class);

    /**
     * Scans base package for annotated components and registers them in container.
     */
    public static void scanPackage()
    {
        Reflections reflections = new Reflections(Obsidian.getBasePackage(), Scanners.TypesAnnotated);
        scanAndRegister(reflections, Repository.class);
        scanAndRegister(reflections, Service.class);
    }

    /**
     * Scans for classes with specific annotation and registers them.
     *
     * @param reflections Reflections instance
     * @param annotation Annotation class to scan for
     */
    private static void scanAndRegister(Reflections reflections, Class<? extends Annotation> annotation)
    {
        Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(annotation);
        for (Class<?> clazz : annotatedClasses) {
            logger.debug("Registering {}: {}", annotation.getSimpleName(), clazz.getSimpleName());
            Container.resolve(clazz);
        }
    }
}