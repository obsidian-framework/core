package com.obsidian.core.event;

/**
 * Dispatched by the {@link EventBus} when an event has no registered handler.
 * Useful in dev to detect events nobody listens to.
 * A {@code DeadEvent} with no handler is not re-dispatched.
 */
public final class DeadEvent implements Event
{
    private final Event event;

    public DeadEvent(Event event)
    {
        this.event = event;
    }

    /**
     * Returns the event that had no handler.
     *
     * @return Original event
     */
    public Event getEvent()
    {
        return event;
    }
}