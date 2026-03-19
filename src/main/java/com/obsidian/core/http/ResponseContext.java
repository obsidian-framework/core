package com.obsidian.core.http;

import spark.Response;

/**
 * Thread-local context for accessing the current HTTP response.
 * Allows controllers and components to access the response without passing it through the call chain.
 */
public class ResponseContext
{
    /** Thread-local storage for the current response */
    private static final ThreadLocal<Response> currentResponse = new ThreadLocal<>();

    /**
     * Sets the response for the current thread.
     *
     * @param response HTTP response
     */
    public static void set(Response response) {
        currentResponse.set(response);
    }

    /**
     * Gets the response for the current thread.
     *
     * @return HTTP response or null if not set
     */
    public static Response get() {
        return currentResponse.get();
    }

    /**
     * Clears the response from the current thread.
     * Should be called after request processing to prevent memory leaks.
     */
    public static void clear() {
        currentResponse.remove();
    }
}