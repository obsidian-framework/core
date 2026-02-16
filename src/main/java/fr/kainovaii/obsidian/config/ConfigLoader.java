package fr.kainovaii.obsidian.config;

import fr.kainovaii.obsidian.config.annotations.Config;
import fr.kainovaii.obsidian.core.Obsidian;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Configuration loader for application startup.
 * Discovers and executes @Config annotated classes in priority order.
 */
public class ConfigLoader
{
    /** Logger instance */
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);

    /**
     * Loads and executes all configuration classes.
     * Configurations are executed in priority order (lower priority first).
     */
    public static void loadConfigurations()
    {
        logger.info("Loading application configurations...");
        try {
            Reflections reflections = new Reflections(Obsidian.getBasePackage());
            Set<Class<?>> configClasses = reflections.getTypesAnnotatedWith(Config.class);

            List<ConfigEntry> configs = new ArrayList<>();

            for (Class<?> configClass : configClasses) {
                Config annotation = configClass.getAnnotation(Config.class);
                configs.add(new ConfigEntry(configClass, annotation.priority()));
            }

            // Sort by priority (lower first)
            configs.sort(Comparator.comparingInt(ConfigEntry::priority));

            // Execute configurations
            for (ConfigEntry entry : configs) {
                executeConfiguration(entry.configClass());
            }

            logger.info("Loaded {} configuration(s)", configs.size());

        } catch (Exception e) {
            logger.error("Failed to load configurations: {}", e.getMessage(), e);
            throw new RuntimeException("Configuration loading failed", e);
        }
    }

    /**
     * Executes configuration class by calling its configure() method.
     *
     * @param configClass Configuration class
     */
    private static void executeConfiguration(Class<?> configClass)
    {
        try {
            Method configureMethod = configClass.getMethod("configure");
            configureMethod.invoke(null);
            logger.info("✓ Configured: {}", configClass.getSimpleName());
        } catch (NoSuchMethodException e) {
            logger.warn("@Config class {} doesn't have a configure() method", configClass.getName());
        } catch (Exception e) {
            logger.error("Failed to execute configuration for {}: {}",
                    configClass.getName(), e.getMessage(), e);
            throw new RuntimeException("Configuration execution failed: " + configClass.getName(), e);
        }
    }

    /**
     * Internal record for sorting configurations by priority.
     */
    private record ConfigEntry(Class<?> configClass, int priority) {}
}