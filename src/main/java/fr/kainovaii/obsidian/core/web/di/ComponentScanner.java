package fr.kainovaii.obsidian.core.web.di;

import fr.kainovaii.obsidian.core.Obsidian;
import fr.kainovaii.obsidian.core.web.di.annotations.Repository;
import fr.kainovaii.obsidian.core.web.di.annotations.Service;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import java.lang.annotation.Annotation;
import java.util.Set;

public class ComponentScanner
{
    public static void scanPackage()
    {
        Reflections reflections = new Reflections(Obsidian.getBasePackage(), Scanners.TypesAnnotated);
        scanAndRegister(reflections, Repository.class);
        scanAndRegister(reflections, Service.class);
    }

    private static void scanAndRegister(Reflections reflections, Class<? extends Annotation> annotation)
    {
        Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(annotation);
        for (Class<?> clazz : annotatedClasses) {
            System.out.println("Registering " + annotation.getSimpleName() + ": " + clazz.getSimpleName());
            Container.resolve(clazz);
        }
    }
}