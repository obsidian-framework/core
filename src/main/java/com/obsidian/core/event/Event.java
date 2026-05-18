package com.obsidian.core.event;

/**
 * Static facade for the event system.
 * Wraps the global {@link EventBus} singleton and exposes a concise
 * dispatch API: {@code Event.dispatch(new UserRegistered(...))}.
 */
public final class Event
{
    /** Global event bus instance. */
    private static final EventBus bus = new EventBus();

    private Event() {}

    /**
     * Dispatches an event to every handler registered for its type.
     *
     * @param event Event instance to dispatch
     */
    public static void dispatch(Object event)
    {
        bus.dispatch(event);
    }

    /**
     * Returns the underlying bus.
     * Used by {@link EventListenerLoader} at boot and by advanced scenarios.
     *
     * @return Global event bus instance
     */
    public static EventBus bus()
    {
        return bus;
    }
}