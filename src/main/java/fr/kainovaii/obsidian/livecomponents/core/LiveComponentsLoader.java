package fr.kainovaii.obsidian.livecomponents.core;

import fr.kainovaii.obsidian.core.Obsidian;
import fr.kainovaii.obsidian.di.Container;
import fr.kainovaii.obsidian.livecomponents.http.LiveComponentController;
import fr.kainovaii.obsidian.livecomponents.pebble.ComponentExtension;
import fr.kainovaii.obsidian.livecomponents.scanner.LiveComponentScanner;
import fr.kainovaii.obsidian.routing.RouteLoader;
import fr.kainovaii.obsidian.validation.pebble.ValidationExtension;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.ClasspathLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * LiveComponents discovery and initialization system.
 * Scans for @LiveComponentImpl annotated classes and registers them with the ComponentManager.
 */
public class LiveComponentsLoader
{
    /** LiveComponents manager */
    private static ComponentManager componentManager;

    /** Logger instance */
    static Logger logger = LoggerFactory.getLogger(LiveComponent.class);

    /**
     * Loads and registers all LiveComponents in application.
     * Discovers @LiveComponentImpl classes, builds Pebble engine with required extensions,
     * and registers the ComponentManager in the DI container.
     */
    public static void loadLiveComponents()
    {
        logger.info("Loading LiveComponents...");
        try {
            ClasspathLoader loader = new ClasspathLoader();

            // Bootstrap engine to instantiate ComponentManager before scanning
            PebbleEngine componentPebble = new PebbleEngine.Builder()
                    .loader(loader)
                    .cacheActive(true)
                    .build();

            componentManager = new ComponentManager(componentPebble);

            // Scan and register all @LiveComponentImpl classes
            LiveComponentScanner.scan(Obsidian.getBasePackage(), componentManager);

            // Rebuild engine with all required Pebble extensions
            componentPebble = new PebbleEngine.Builder()
                    .loader(loader)
                    .extension(new ComponentExtension(componentManager))
                    .extension(new ValidationExtension())
                    .cacheActive(true)
                    .build();

            // Inject final engine into manager via reflection
            java.lang.reflect.Field field = ComponentManager.class.getDeclaredField("pebbleEngine");
            field.setAccessible(true);
            field.set(componentManager, componentPebble);

            Container.singleton(ComponentManager.class, componentManager);
            List<Object> frameworkControllers = List.of(new LiveComponentController());
            RouteLoader.registerRoutes(frameworkControllers);

            logger.info("LiveComponents loaded successfully!");
        } catch (Exception e) {
            logger.error("Failed to load LiveComponents: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Returns the initialized ComponentManager instance.
     * Must be called after loadLiveComponents().
     *
     * @return The singleton ComponentManager, or null if initialization failed
     */
    public static ComponentManager getComponentManager() { return componentManager; }
}