package com.obsidian.core.database.seeder;

import com.obsidian.core.database.seeder.annotations.Seeder;
import com.obsidian.core.di.Container;
import com.obsidian.core.di.ReflectionsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Seeder loader for database population at startup.
 * Discovers and executes @Seeder annotated classes in priority order.
 * Must be called after database initialization.
 */
public class SeederLoader
{
    /** Logger instance */
    private static final Logger logger = LoggerFactory.getLogger(SeederLoader.class);

    /**
     * Loads and executes all seeder classes.
     * Seeders are executed in priority order (lower priority first).
     */
    public static void loadSeeders()
    {
        logger.info("Loading seeders...");
        try {
            String basePackage = com.obsidian.core.core.Obsidian.getBasePackage();
            Set<Class<?>> seederClasses = ReflectionsProvider.getTypesAnnotatedWith(Seeder.class);

            List<SeederEntry> seeders = new ArrayList<>();

            for (Class<?> seederClass : seederClasses) {
                if (!seederClass.getName().startsWith(basePackage)) {
                    logger.debug("Skipping seeder outside base package: {}", seederClass.getName());
                    continue;
                }
                Seeder annotation = seederClass.getAnnotation(Seeder.class);
                seeders.add(new SeederEntry(seederClass, annotation.priority()));
            }

            // Sort by priority (lower first)
            seeders.sort(Comparator.comparingInt(SeederEntry::priority));

            // Execute seeders
            for (SeederEntry entry : seeders) {
                executeSeeder(entry.seederClass());
            }

            logger.info("Loaded {} seeder(s)", seeders.size());

        } catch (Exception e) {
            logger.error("Failed to load seeders: {}", e.getMessage(), e);
            throw new RuntimeException("Seeder loading failed", e);
        }
    }

    /**
     * Executes a seeder class by instantiating it and calling its seed() method.
     *
     * @param seederClass Seeder class
     */
    private static void executeSeeder(Class<?> seederClass)
    {
        try {
            Object instance = seederClass.getDeclaredConstructor().newInstance();
            Container.injectFields(instance);

            if (instance instanceof SeederInterface seeder) {
                seeder.seed();
                logger.info("✔ Seeded: {}", seederClass.getSimpleName());
            } else {
                logger.warn("@Seeder class {} does not implement SeederInterface", seederClass.getName());
            }
        } catch (Exception e) {
            logger.error("Failed to execute seeder for {}: {}",
                    seederClass.getName(), e.getMessage(), e);
            throw new RuntimeException("Seeder execution failed: " + seederClass.getName(), e);
        }
    }

    /**
     * Internal record for sorting seeders by priority.
     */
    private record SeederEntry(Class<?> seederClass, int priority) {}
}