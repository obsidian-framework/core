package com.obsidian.core.http.middleware;

import spark.Request;
import spark.Response;

/**
 * Middleware interface for request/response processing.
 * Implement this interface to create custom middleware.
 */
public interface Middleware {
    /**
     * Handles request/response processing.
     *
     * @param req HTTP request
     * @param res HTTP response
     * @throws Exception if processing fails
     */
    void handle(Request req, Response res) throws Exception;
}