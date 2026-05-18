package com.obsidian.core.event;

/**
 * Static facade for the event system.
 *
 * <p>Usage:
 * <pre>
 *  Event.dispatch(new UserRegistered("alice@example.com"));
 * </pre>
 */
public final class Event
{
    private static final EventBus bus = new EventBus();

    private Event() {}

    /**
     * Dispatches an event to every handler registered for its type.
     */
    public static void dispatch(Object event)
    {
        bus.dispatch(event);
    }

    /**
     * Returns the underlying bus. Used by {@link EventListenerLoader}
     * and advanced scenarios.
     */
    public static EventBus bus()
    {
        return bus;
    }
}