package fr.kainovaii.obsidian.di;

import fr.kainovaii.obsidian.core.EnvLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads and executes service providers at application boot.
 * Reads the {@code PROVIDERS} environment variable, instantiates each provider,
 * and calls {@link ServiceProvider#register()}.
 */
public class ServiceProviderLoader
{
    private static final Logger logger = LoggerFactory.getLogger(ServiceProviderLoader.class);

    /**
     * Loads all service providers declared in the {@code PROVIDERS} environment variable.
     * Expects a comma-separated list of fully qualified class names.
     * No-op if {@code PROVIDERS} is not set.
     */
    public static void load() {
        String raw = EnvLoader.getInstance().get("PROVIDERS", "");
        if (raw.isBlank()) return;

        for (String className : raw.split(",")) {
            className = className.trim();
            if (className.isEmpty()) continue;
            register(className);
        }
    }

    /**
     * Instantiates and registers a single service provider by class name.
     *
     * @param className The fully qualified class name of the provider
     */
    private static void register(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            ServiceProvider provider = (ServiceProvider) clazz.getDeclaredConstructor().newInstance();
            provider.register();
            logger.debug("Registered provider: {}", className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Service provider not found: " + className, e);
        } catch (ClassCastException e) {
            throw new RuntimeException("Not a ServiceProvider: " + className, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to register provider: " + className, e);
        }
    }
}