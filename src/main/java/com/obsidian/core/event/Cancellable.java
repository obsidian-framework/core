package com.obsidian.core.event;

public interface Cancellable extends Event
{
    void cancel();
    boolean isCancelled();
}
