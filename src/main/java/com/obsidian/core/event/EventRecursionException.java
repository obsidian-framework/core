package com.obsidian.core.event;

/**
 * Thrown when event dispatch nests beyond the configured maximum depth,
 * indicating a likely infinite loop between listeners that re-dispatch
 * each other's events.
 *
 * @see EventBus#setMaxDispatchDepth(int)
 */
public class EventRecursionException extends RuntimeException
{
    public EventRecursionException(String message) {
        super(message);
    }
}
