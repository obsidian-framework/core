package com.obsidian.core.database.orm.model;

public class ModelNotFoundException extends RuntimeException {

    /**
     * Creates a new ModelNotFoundException instance.
     *
     * @param message The message
     */
    public ModelNotFoundException(String message) {
        super(message);
    }
}
