package com.obsidian.core.di;

import com.obsidian.core.core.EnvLoader;

/**
 * Base class for service providers.
 * Each provider encapsulates the registration of a subsystem into the DI container.
 * Providers are loaded at boot via {@link ServiceProviderLoader}.
 */
public abstract class ServiceProvider
{
    /**
     * Registers services into the DI container.
     * Called once at application boot.
     */
    public abstract void register();

    /**
     * Retrieves an environment variable with a default value.
     *
     * @param key          The variable name
     * @param defaultValue The default value if key is not found
     * @return The variable value, or defaultValue if not found
     */
    protected String env(String key, String defaultValue) {
        return EnvLoader.getInstance().get(key, defaultValue);
    }

    /**
     * Registers a singleton instance into the DI container.
     *
     * @param clazz    The class type to register
     * @param instance The singleton instance
     * @param <T>      Type parameter
     */
    protected <T> void singleton(Class<T> clazz, T instance) {
        Container.singleton(clazz, instance);
    }

    /**
     * Binds an interface to a concrete implementation in the DI container.
     *
     * @param abstraction    The interface or abstract class
     * @param implementation The concrete implementation
     * @param <T>            Type parameter
     */
    protected <T> void bind(Class<T> abstraction, Class<? extends T> implementation) {
        Container.bind(abstraction, implementation);
    }
}