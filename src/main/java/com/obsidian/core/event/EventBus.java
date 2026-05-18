package com.obsidian.core.event;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central event dispatcher.
 * Routes dispatched events to every handler registered for their type.
 */
public final class EventBus
{
    private final ConcurrentHashMap<Class<?>, List<RegisteredHandler>> listenersByType = new ConcurrentHashMap<>();

    /**
     * Registers a handler method for a given event type.
     * Typically called by {@link EventListenerLoader} at boot.
     *
     * @param eventType        Event class this handler listens to
     * @param listenerInstance Listener instance owning the handler method
     * @param method           Handler method to invoke
     */
    public void register(Class<?> eventType, Object listenerInstance, Method method)
    {
        Objects.requireNonNull(eventType,        "eventType must not be null");
        Objects.requireNonNull(listenerInstance, "listenerInstance must not be null");
        Objects.requireNonNull(method,           "method must not be null");

        method.setAccessible(true);
        RegisteredHandler handler = new RegisteredHandler(listenerInstance, method);

        listenersByType.compute(eventType, (k, existing) ->
        {
            List<RegisteredHandler> updated = (existing == null) ? new ArrayList<>() : new ArrayList<>(existing);
            updated.add(handler);
            return Collections.unmodifiableList(updated);
        });
    }

    /**
     * Dispatches an event to every handler registered for its exact type.
     *
     * @param event Event instance to dispatch
     */
    public void dispatch(Object event)
    {
        Objects.requireNonNull(event, "event must not be null");

        List<RegisteredHandler> handlers = listenersByType.get(event.getClass());
        if (handlers == null || handlers.isEmpty()) return;

        for (RegisteredHandler h : handlers) {
            invoke(h, event);
        }
    }

    /**
     * Invokes a single handler with the given event.
     *
     * @param handler Registered handler to invoke
     * @param event   Event instance
     */
    private void invoke(RegisteredHandler handler, Object event)
    {
        try {
            handler.method.invoke(handler.instance, event);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("Handler " + handler.instance.getClass().getSimpleName() + "#" + handler.method.getName() + " threw on " + event.getClass().getSimpleName(), cause);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot invoke handler " + handler.instance.getClass().getSimpleName() + "#" + handler.method.getName(), e);
        }
    }

    /**
     * Internal record for a registered handler (instance + method).
     */
    private static final class RegisteredHandler
    {
        final Object instance;
        final Method method;

        RegisteredHandler(Object instance, Method method)
        {
            this.instance = instance;
            this.method   = method;
        }
    }
}