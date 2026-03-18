package com.obsidian.core.config;

import com.obsidian.core.config.annotations.Config;
import com.obsidian.core.core.Obsidian;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * Executes a configuration class by instantiating it and calling its configure() method.
     *
     * @param configClass Configuration class
     */
    private static void executeConfiguration(Class<?> configClass)
    {
        try {
            Object instance = configClass.getDeclaredConstructor().newInstance();

            if (instance instanceof ConfigInterface config) {
                config.configure();
                logger.info("✔ Configured: {}", configClass.getSimpleName());
            } else {
                logger.warn("@Config class {} does not implement ConfigInterface", configClass.getName());
            }
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