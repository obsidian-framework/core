package fr.kainovaii.obsidian.livecomponents.scanner;

import fr.kainovaii.obsidian.livecomponents.annotations.LiveComponentImpl;
import fr.kainovaii.obsidian.livecomponents.core.ComponentManager;
import fr.kainovaii.obsidian.livecomponents.core.LiveComponent;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.util.Set;
import java.util.logging.Logger;

/**
 * Scanner for discovering and registering LiveComponents.
 * Scans package for @LiveComponent annotated classes and registers them with ComponentManager.
 */
public class LiveComponentScanner {

    /** Logger instance */
    private static final Logger logger = Logger.getLogger(LiveComponentScanner.class.getName());

    /**
     * Scans package for LiveComponent classes and registers them.
     *
     * @param basePackage Package to scan
     * @param componentManager Component manager for registration
     */
    public static void scan(String basePackage, ComponentManager componentManager)
    {
        logger.info("Scanning for LiveComponents in package: " + basePackage);

        try {
            Reflections reflections = new Reflections(
                    new ConfigurationBuilder()
                            .forPackage(basePackage)
                            .setScanners(Scanners.TypesAnnotated, Scanners.SubTypes)
            );

            Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(
                    LiveComponentImpl.class
            );

            for (Class<?> clazz : annotatedClasses) {
                if (LiveComponent.class.isAssignableFrom(clazz)) {
                    registerComponent(clazz, componentManager);
                } else {
                    logger.warning("Class " + clazz.getName() + " has @LiveComponent but doesn't extend LiveComponent");
                }
            }

            logger.info("Found and registered " + annotatedClasses.size() + " LiveComponents");
        } catch (Exception e) {
            logger.severe("Failed to scan for LiveComponents: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Registers a single component with manager.
     * Uses annotation value as name, or class simple name if not specified.
     *
     * @param clazz Component class
     * @param componentManager Component manager
     */
    @SuppressWarnings("unchecked")
    private static void registerComponent(Class<?> clazz, ComponentManager componentManager)
    {
        LiveComponentImpl annotation =
                clazz.getAnnotation(LiveComponentImpl.class);

        String componentName = (annotation.value() != null && !annotation.value().isEmpty())
                ? annotation.value()
                : clazz.getSimpleName();

        componentManager.register(componentName, (Class<? extends LiveComponent>) clazz);
        logger.info("Registered LiveComponent: " + componentName + " (" + clazz.getName() + ")");
    }
}