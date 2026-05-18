package com.obsidian.core.event;

import com.obsidian.core.di.ReflectionsProvider;
import com.obsidian.core.event.annotations.Listener;
import com.obsidian.core.event.annotations.On;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Event listener loader for application startup.
 * Discovers @Listener annotated classes and registers their @On methods
 * on the global {@link Event} bus.
 */
public class EventListenerLoader
{
    /** Logger instance */
    private static final Logger logger = LoggerFactory.getLogger(EventListenerLoader.class);

    /**
     * Loads and registers all event listeners.
     */
    public static void loadListeners()
    {
        logger.info("Loading event listeners...");
        try {
            Set<Class<?>> listenerClasses = ReflectionsProvider.getTypesAnnotatedWith(Listener.class);

            int handlerCount = 0;

            for (Class<?> listenerClass : listenerClasses) {
                handlerCount += registerListener(listenerClass);
            }

            logger.info("Loaded {} event handler(s) from {} listener(s)", handlerCount, listenerClasses.size());

        } catch (Exception e) {
            logger.error("Failed to load event listeners: {}", e.getMessage(), e);
            throw new RuntimeException("Event listener loading failed", e);
        }
    }

    /**
     * Instantiates a listener class and registers each of its @On methods.
     *
     * @param listenerClass Listener class
     * @return number of @On methods registered
     */
    private static int registerListener(Class<?> listenerClass)
    {
        try {
            Object instance = listenerClass.getDeclaredConstructor().newInstance();
            int count = 0;

            for (Method method : listenerClass.getDeclaredMethods()) {
                On on = method.getAnnotation(On.class);
                if (on == null) continue;

                Event.bus().register(on.value(), instance, method);
                count++;
            }

            if (count == 0) {
                logger.warn("@Listener class {} has no @On methods", listenerClass.getName());
            } else {
                logger.info("✔ Registered: {} ({} handler{})", listenerClass.getSimpleName(), count, count > 1 ? "s" : "");
            }

            return count;
        } catch (Exception e) {
            logger.error("Failed to register listener {}: {}", listenerClass.getName(), e.getMessage(), e);
            throw new RuntimeException("Listener registration failed: " + listenerClass.getName(), e);
        }
    }
}