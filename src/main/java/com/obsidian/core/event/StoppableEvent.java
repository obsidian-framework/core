package com.obsidian.core.event;

/**
 * Optional interface for events that can be canceled by a listener.
 */
public interface StoppableEvent
{
    /**
     * @return true if a previous listener has called {@link #stopPropagation()}
     */
    boolean isPropagationStopped();

    /**
     * Marks this event as cancelled. Listeners registered after the current
     * one (in priority order) will not be invoked.
     */
    void stopPropagation();
}
