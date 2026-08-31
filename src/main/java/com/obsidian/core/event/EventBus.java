package com.obsidian.core.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Central event dispatcher.
 * Routes dispatched events to every handler registered for their type.
 */
public final class EventBus
{
    /** Logger instance */
    private static final Logger logger = LoggerFactory.getLogger(EventBus.class);

    private final ConcurrentHashMap<Class<? extends Event>, List<RegisteredHandler>> listenersByType = new ConcurrentHashMap<>();

    private final List<Consumer<Event>> globalListeners = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<Event> listener) { globalListeners.add(listener); }

    private static final AtomicLong HANDLER_ID_COUNTER = new AtomicLong();

    /**
     * Registers a handler method for a given event type.
     * Typically called by {@link EventListenerLoader} at boot.
     *
     * @param eventType        Event class this handler listens to
     * @param listenerInstance Listener instance owning the handler method
     * @param method           Handler method to invoke
     * @return
     */
    public long register(Class<? extends Event> eventType, Object listenerInstance, Method method, int priority)
    {
        Objects.requireNonNull(eventType,        "eventType must not be null");
        Objects.requireNonNull(listenerInstance, "listenerInstance must not be null");
        Objects.requireNonNull(method,           "method must not be null");

        method.setAccessible(true);
        long handlerId = HANDLER_ID_COUNTER.incrementAndGet();
        RegisteredHandler handler = new RegisteredHandler(handlerId, listenerInstance, method, priority);

        listenersByType.compute(eventType, (k, existing) ->
        {
            List<RegisteredHandler> updated = (existing == null) ? new ArrayList<>() : new ArrayList<>(existing);
            updated.add(handler);
            updated.sort(Comparator.comparingInt((RegisteredHandler h) -> h.priority).reversed());
            return Collections.unmodifiableList(updated);
        });
        return handlerId;
    }

    /**
     * Unregisters a previously registered handler.
     *
     * @param eventType Event type the handler was registered for
     * @param handlerId Handler ID returned by {@link #register}
     */
    public void unregister(Class<? extends Event> eventType, long handlerId)
    {
        Objects.requireNonNull(eventType, "eventType must not be null");

        listenersByType.compute(eventType, (k, existing) ->
        {
            if (existing == null) return null;

            List<RegisteredHandler> updated = new ArrayList<>(existing);
            updated.removeIf(h -> h.handlerId == handlerId);

            return updated.isEmpty() ? null : Collections.unmodifiableList(updated);
        });
    }

    /**
     * Dispatches an event to every handler registered for its exact type.
     * A handler that throws is logged and skipped — other handlers still run.
     *
     * @param event Event instance to dispatch
     */
    public void dispatch(Event event)
    {
        Objects.requireNonNull(event, "event must not be null");

        List<RegisteredHandler> handlers = listenersByType.get(event.getClass());
        if (handlers == null || handlers.isEmpty())
        {
            if (event instanceof Cancellable) return;
            for (Consumer<Event> g : globalListeners) {
                try { g.accept(event); }
                catch (Throwable t) { logger.error("Global listener threw", t); }
            }
            return;
        }

        for (RegisteredHandler h : handlers)
        {
            invokeSafely(h, event);
            if (event instanceof Cancellable && ((Cancellable) event).isCancelled()) {
                return;
            }
        }

        for (Consumer<Event> g : globalListeners) {
            try { g.accept(event); }
            catch (Throwable t) { logger.error("Global listener threw", t); }
        }
    }

    /**
     * Invokes a single handler with the given event.
     * Exceptions thrown by the handler are logged but never propagate.
     * VM-level errors (OOM, StackOverflow) propagate normally.
     *
     * @param handler Registered handler to invoke
     * @param event   Event instance
     */
    private void invokeSafely(RegisteredHandler handler, Event event)
    {
        try {
            handler.method.invoke(handler.instance, event);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof VirtualMachineError) {
                throw (VirtualMachineError) cause;
            }
            logger.error("Handler {}#{} threw on {}: {}", handler.instance.getClass().getSimpleName(), handler.method.getName(), event.getClass().getSimpleName(), cause.getMessage(), cause);
        } catch (IllegalAccessException e) {
            logger.error("Cannot invoke handler {}#{} (illegal access)", handler.instance.getClass().getSimpleName(), handler.method.getName(), e);
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError) {
                throw (VirtualMachineError) t;
            }
            logger.error("Unexpected error invoking handler {}#{} on {}", handler.instance.getClass().getSimpleName(), handler.method.getName(), event.getClass().getSimpleName(), t);
        }
    }

    /**
     * Internal record for a registered handler (instance + method).
     */
    private static final class RegisteredHandler
    {
        final long handlerId;
        final Object instance;
        final Method method;
        final int priority;

        RegisteredHandler(long handlerId, Object instance, Method method, int priority)
        {
            this.handlerId = handlerId;
            this.instance = instance;
            this.method   = method;
            this.priority = priority;
        }
    }
}