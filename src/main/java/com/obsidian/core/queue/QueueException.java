package com.obsidian.core.queue;

/**
 * Unchecked exception thrown by the queue system for infrastructure-level failures
 * (serialization errors, DB unreachable, driver misconfiguration, etc.).
 */
public class QueueException extends RuntimeException
{
    public QueueException(String message) {
        super(message);
    }

    public QueueException(String message, Throwable cause) {
        super(message, cause);
    }
}