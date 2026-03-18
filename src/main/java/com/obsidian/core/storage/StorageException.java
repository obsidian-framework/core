package com.obsidian.core.storage;

/**
 * Unchecked exception thrown by storage operations on failure.
 */
public class StorageException extends RuntimeException
{
    /**
     * Creates a new StorageException.
     *
     * @param message A description of the failure
     * @param cause   The underlying cause, or null if none
     */
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}