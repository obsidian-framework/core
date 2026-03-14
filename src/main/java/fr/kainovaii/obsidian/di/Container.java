package fr.kainovaii.obsidian.di;

import fr.kainovaii.obsidian.di.annotations.Inject;
import fr.kainovaii.obsidian.di.annotations.Repository;
import fr.kainovaii.obsidian.di.annotations.Service;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dependency injection container.
 * Manages singleton instances and handles automatic dependency resolution.
 * Supports both constructor injection and field injection via @Inject.
 * Thread-safe: singletons and bindings use ConcurrentHashMap;
 * circular dependency detection is per-thread via ThreadLocal.
 */
public class Container
{
    /** Singleton instances cache */
    private static final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();

    /** Interface to implementation bindings */
    private static final Map<Class<?>, Class<?>> bindings = new ConcurrentHashMap<>();

    /**
     * Tracks classes currently being resolved per thread to detect circular dependencies.
     * ThreadLocal ensures each resolution chain is isolated to its own thread.
     */
    private static final ThreadLocal<Set<Class<?>>> resolving = ThreadLocal.withInitial(HashSet::new);

    /** Allowed component annotations */
    private static final Set<Class<? extends Annotation>> COMPONENT_ANNOTATIONS = Set.of(Service.class, Repository.class);

    /**
     * Registers a singleton instance manually.
     *
     * @param clazz Class type
     * @param instance Instance to register
     * @param <T> Type parameter
     */
    public static <T> void singleton(Class<T> clazz, T instance) {
        singletons.put(clazz, instance);
    }

    /**
     * Binds an interface to its implementation.
     *
     * @param abstraction Interface or abstract class
     * @param implementation Concrete implementation
     * @param <T> Type parameter
     */
    public static <T> void bind(Class<T> abstraction, Class<? extends T> implementation) {
        bindings.put(abstraction, implementation);
    }

    /**
     * Resolves a class instance with automatic dependency injection.
     * Supports constructor injection and field injection via @Inject.
     *
     * @param clazz Class to resolve
     * @param <T> Type parameter
     * @return Resolved singleton instance
     * @throws IllegalArgumentException if class is not an annotated component
     * @throws RuntimeException if a circular dependency or instantiation error is detected
     */
    @SuppressWarnings("unchecked")
    public static <T> T resolve(Class<T> clazz)
    {
        // Resolve via bindings first (supports interface injection)
        Class<?> resolvedClass = bindings.getOrDefault(clazz, clazz);

        // Return existing singleton
        if (singletons.containsKey(resolvedClass)) {
            return (T) singletons.get(resolvedClass);
        }

        // Guard: only annotated components or manually bound classes are allowed
        if (!isComponent(resolvedClass) && !bindings.containsKey(clazz)) {
            throw new IllegalArgumentException(
                    "Cannot resolve '" + resolvedClass.getSimpleName() + "': " +
                    "class must be annotated with @Service or @Repository, " +
                    "or registered manually via Container.bind() / Container.singleton()."
            );
        }

        // Guard: circular dependency detection
        if (resolving.get().contains(resolvedClass)) {
            throw new RuntimeException(
                    "Circular dependency detected while resolving: " + resolvedClass.getName()
            );
        }

        resolving.get().add(resolvedClass);
        try {
            Constructor<?> constructor = selectConstructor(resolvedClass);
            constructor.setAccessible(true);

            Class<?>[] paramTypes = constructor.getParameterTypes();
            Object[] params = new Object[paramTypes.length];

            for (int i = 0; i < paramTypes.length; i++) {
                params[i] = resolve(paramTypes[i]);
            }

            T instance = (T) constructor.newInstance(params);
            singletons.put(resolvedClass, instance);

            // Inject @Inject-annotated fields
            injectFields(instance);

            return instance;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Cannot resolve dependency: " + resolvedClass.getName(), e);
        } finally {
            resolving.get().remove(resolvedClass);
        }
    }

    /**
     * Injects dependencies into @Inject-annotated fields of an existing instance.
     * Useful for classes not managed by the container (e.g. Seeders).
     *
     * @param instance Instance to inject into
     */
    public static void injectFields(Object instance)
    {
        Class<?> clazz = instance.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Inject.class)) continue;
            try {
                field.setAccessible(true);
                Object dependency = resolve(field.getType());
                field.set(instance, dependency);
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to inject field '" + field.getName() + "' in " + clazz.getSimpleName(), e
                );
            }
        }
    }

    /**
     * Selects the constructor to use for injection.
     *
     * @param clazz Class to inspect
     * @return Constructor to use
     * @throws IllegalArgumentException if ambiguous constructors without @Inject
     */
    private static Constructor<?> selectConstructor(Class<?> clazz)
    {
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();

        // Look for @Inject-annotated constructor
        Constructor<?>[] injected = Arrays.stream(constructors)
                .filter(c -> c.isAnnotationPresent(Inject.class))
                .toArray(Constructor[]::new);

        if (injected.length == 1) {
            return injected[0];
        }

        if (injected.length > 1) {
            throw new IllegalArgumentException("'" + clazz.getSimpleName() + "' has multiple constructors annotated with @Inject. Only one is allowed.");
        }

        // No @Inject: only valid if there is exactly one constructor
        if (constructors.length == 1) {
            return constructors[0];
        }

        throw new IllegalArgumentException("'" + clazz.getSimpleName() + "' has " + constructors.length + " constructors. " + "Annotate the one to use with @Inject.");
    }

    /**
     * Checks whether a class is an annotated component.
     *
     * @param clazz Class to check
     * @return true if annotated with @Service or @Repository
     */
    private static boolean isComponent(Class<?> clazz)
    {
        return COMPONENT_ANNOTATIONS.stream().anyMatch(clazz::isAnnotationPresent);
    }

    /**
     * Clears all singletons and bindings.
     * Useful for testing or reinitialization.
     */
    public static void clear()
    {
        singletons.clear();
        bindings.clear();
        resolving.remove();
    }
}