package fr.kainovaii.obsidian.livecomponents.http;

import spark.Request;

/**
 * Thread-local context for accessing the current HTTP request.
 * Allows LiveComponents to access the request without passing it through the call chain.
 */
public class RequestContext
{
    /** Thread-local storage for the current request */
    private static final ThreadLocal<Request> currentRequest = new ThreadLocal<>();

    /**
     * Sets the request for the current thread.
     *
     * @param request HTTP request
     */
    public static void set(Request request) {
        currentRequest.set(request);
    }

    /**
     * Gets the request for the current thread.
     *
     * @return HTTP request or null if not set
     */
    public static Request get() {
        return currentRequest.get();
    }

    /**
     * Clears the request from the current thread.
     * Should be called after request processing to prevent memory leaks.
     */
    public static void clear() {
        currentRequest.remove();
    }
}