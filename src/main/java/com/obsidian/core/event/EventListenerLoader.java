package com.obsidian.core.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Discovers {@link Listener}-annotated classes and registers their
 * {@link On}-annotated methods on an {@link EventBus}.
 */
public final class EventListenerLoader
{
    private static final Logger logger = LoggerFactory.getLogger(EventListenerLoader.class);

    private EventListenerLoader() {}
    /**
     * Registers every {@code @On} method on every {@code @Listener} object.
     *
     * @param bus       target event bus
     * @param listeners pre-instantiated listener objects (typically resolved by the DI container)
     * @return number of handler methods successfully registered
     */
    public static int registerListeners(EventBus bus, List<Object> listeners) {
        int registered = 0;

        for (Object instance : listeners) {
            Class<?> clazz = instance.getClass();

            if (!clazz.isAnnotationPresent(Listener.class)) {
                logger.warn("Object of type {} passed to EventListenerLoader is not annotated " + "with @Listener — skipping", clazz.getName());
                continue;
            }

            registered += registerOne(bus, instance);
        }

        logger.info("Loaded {} event listener method(s)", registered);
        return registered;
    }

    private static int registerOne(EventBus bus, Object instance)
    {
        int count = 0;
        Class<?> clazz = instance.getClass();

        for (Method method : clazz.getDeclaredMethods()) {
            On on = method.getAnnotation(On.class);
            if (on == null) continue;

            if (!validateSignature(clazz, method, on)) continue;

            bus.register(on.value(), instance, method, on.priority());
            count++;
        }

        if (count == 0) {
            logger.warn("Listener class {} has no @On methods — did you forget the annotation?", clazz.getName());
        }

        return count;
    }

    private static boolean validateSignature(Class<?> clazz, Method method, On on) {
        Class<?>[] params = method.getParameterTypes();

        if (params.length != 1) {
            logger.error("Listener method {}#{} must take exactly one parameter, got {} — skipping", clazz.getSimpleName(), method.getName(), params.length);
            return false;
        }

        Class<?> declaredEventType = params[0];
        Class<?> annotatedEventType = on.value();

        if (!declaredEventType.isAssignableFrom(annotatedEventType)) {
            logger.error(
                "Listener method {}#{} declares parameter {} but @On specifies {} — these are " +
                "incompatible (the declared type must be assignable from the annotated type). Skipping.",
                clazz.getSimpleName(), method.getName(),
                declaredEventType.getName(), annotatedEventType.getName());
            return false;
        }

        if (method.getReturnType() != void.class) {
            logger.warn("Listener method {}#{} returns {} — return value will be ignored", clazz.getSimpleName(), method.getName(), method.getReturnType().getSimpleName());
        }

        return true;
    }
}
