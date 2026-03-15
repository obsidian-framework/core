package fr.kainovaii.obsidian.livecomponents.core;

import fr.kainovaii.obsidian.core.Obsidian;
import fr.kainovaii.obsidian.di.Container;
import fr.kainovaii.obsidian.livecomponents.http.LiveComponentController;
import fr.kainovaii.obsidian.livecomponents.pebble.ComponentExtension;
import fr.kainovaii.obsidian.livecomponents.pebble.ComponentTagExtension;
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
 * Scans for {@code @LiveComponentImpl}-annotated classes, registers them with the
 * {@link ComponentManager}, and wires all required Pebble extensions.
 */
public class LiveComponentsLoader
{
    private static ComponentManager componentManager;
    static Logger logger = LoggerFactory.getLogger(LiveComponent.class);

    public static void loadLiveComponents()
    {
        logger.info("Loading LiveComponents...");
        try {
            ClasspathLoader loader = new ClasspathLoader();

            PebbleEngine bootstrapEngine = new PebbleEngine.Builder()
                    .loader(loader)
                    .cacheActive(true)
                    .build();

            componentManager = new ComponentManager(bootstrapEngine);

            LiveComponentScanner.scan(Obsidian.getBasePackage(), componentManager);

            // Final engine — includes both the function-based and tag-based component extensions
            componentManager.setPebbleEngine(new PebbleEngine.Builder()
                    .loader(loader)
                    .extension(new ComponentExtension(componentManager))
                    .extension(new ComponentTagExtension())
                    .extension(new ValidationExtension())
                    .cacheActive(true)
                    .build());

            Container.singleton(ComponentManager.class, componentManager);
            RouteLoader.registerRoutes(List.of(new LiveComponentController()));

            logger.info("LiveComponents loaded successfully!");
        } catch (Exception e) {
            logger.error("Failed to load LiveComponents: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static ComponentManager getComponentManager() { return componentManager; }
}