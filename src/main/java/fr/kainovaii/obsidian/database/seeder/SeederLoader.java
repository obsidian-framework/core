package fr.kainovaii.obsidian.database.seeder;

import fr.kainovaii.obsidian.core.Obsidian;
import fr.kainovaii.obsidian.database.seeder.annotations.Seeder;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
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
            Reflections reflections = new Reflections(Obsidian.getBasePackage());
            Set<Class<?>> seederClasses = reflections.getTypesAnnotatedWith(Seeder.class);

            List<SeederEntry> seeders = new ArrayList<>();

            for (Class<?> seederClass : seederClasses) {
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
     * Executes seeder class by calling its seed() method.
     *
     * @param seederClass Seeder class
     */
    private static void executeSeeder(Class<?> seederClass)
    {
        try {
            Method seedMethod = seederClass.getMethod("seed");
            seedMethod.invoke(null);
            logger.info("✓ Seeded: {}", seederClass.getSimpleName());
        } catch (NoSuchMethodException e) {
            logger.warn("@Seeder class {} doesn't have a seed() method", seederClass.getName());
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