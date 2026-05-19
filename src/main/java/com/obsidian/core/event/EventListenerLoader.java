package com.obsidian.core.event;

import com.obsidian.core.di.Container;
import com.obsidian.core.di.ReflectionsProvider;
import com.obsidian.core.event.annotations.Listener;
import com.obsidian.core.event.annotations.On;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/**
 * Event listener loader for application startup.
 * Discovers @Listener annotated classes and registers their @On methods
 * on the global {@link Events} bus.
 */
public class EventListenerLoader
{
    /** Logger instance */
    private static final Logger logger = LoggerFactory.getLogger(EventListenerLoader.class);

    /**
     * Loads and registers all event listeners discovered via classpath scan.
     */
    public static void loadListeners()
    {
        logger.info("Loading event listeners...");
        try {
            Set<Class<?>> listenerClasses = ReflectionsProvider.getTypesAnnotatedWith(Listener.class);

            int handlerCount = 0;

            for (Class<?> listenerClass : listenerClasses) {
                handlerCount += registerListenerClass(Events.bus(), listenerClass);
            }

            logger.info("Loaded {} event handler(s) from {} listener(s)", handlerCount, listenerClasses.size());

        } catch (Exception e) {
            logger.error("Failed to load event listeners: {}", e.getMessage(), e);
            throw new RuntimeException("Event listener loading failed", e);
        }
    }

    /**
     * Registers a pre-instantiated list of listeners on the given bus.
     * Useful for tests and DI-managed listeners.
     *
     * @param bus       Target event bus
     * @param listeners Pre-instantiated listener objects
     * @return number of @On methods registered
     */
    public static int registerListeners(EventBus bus, List<Object> listeners)
    {
        int count = 0;

        for (Object instance : listeners) {
            Class<?> clazz = instance.getClass();

            if (!clazz.isAnnotationPresent(Listener.class)) {
                logger.warn("Object of type {} is not annotated with @Listener — skipping", clazz.getName());
                continue;
            }

            count += registerListenerInstance(bus, instance);
        }

        return count;
    }

    /**
     * Instantiates a listener class and registers its @On methods.
     *
     * @param bus           Target event bus
     * @param listenerClass Listener class
     * @return number of @On methods registered
     */
    private static int registerListenerClass(EventBus bus, Class<?> listenerClass)
    {
        try {
            Object instance = Container.instantiate(listenerClass, Object.class);
            int count = registerListenerInstance(bus, instance);

            if (count > 0) {
                logger.info("✔ Registered: {} ({} handler{})", listenerClass.getSimpleName(), count, count > 1 ? "s" : "");
            } else {
                logger.warn("@Listener class {} has no @On methods", listenerClass.getName());
            }

            return count;
        } catch (Exception e) {
            logger.error("Failed to register listener {}: {}", listenerClass.getName(), e.getMessage(), e);
            throw new RuntimeException("Listener registration failed: " + listenerClass.getName(), e);
        }
    }

    /**
     * Registers every @On method of an already-instantiated listener.
     *
     * @param bus      Target event bus
     * @param instance Listener instance
     * @return number of @On methods registered
     */
    private static int registerListenerInstance(EventBus bus, Object instance)
    {
        int count = 0;

        for (Method method : instance.getClass().getDeclaredMethods()) {
            On on = method.getAnnotation(On.class);
            if (on == null) continue;

            bus.register(on.value(), instance, method, on.priority());
            count++;
        }

        return count;
    }
}