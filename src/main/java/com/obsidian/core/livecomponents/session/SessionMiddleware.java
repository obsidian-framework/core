package com.obsidian.core.livecomponents.session;

import com.obsidian.core.http.middleware.Middleware;
import com.obsidian.core.livecomponents.http.RequestContext;
import spark.Request;
import spark.Response;

/**
 * Middleware that sets the session and request in their respective thread-local contexts.
 * Enables LiveComponents to access the session via {@link SessionContext} and
 * the request via {@link RequestContext} without passing them through the call chain.
 */
public class SessionMiddleware implements Middleware
{
    /**
     * Handles the request by storing the session and request in thread-local contexts.
     *
     * @param req HTTP request
     * @param res HTTP response
     * @throws Exception if processing fails
     */
    @Override
    public void handle(Request req, Response res) throws Exception
    {
        try {
            SessionContext.set(req.session(true));
            RequestContext.set(req);
        } catch (Exception e) {
            SessionContext.clear();
            RequestContext.clear();
        }
    }

    /**
     * Clears thread-local contexts after request processing.
     * Must be called (or wired as an after-filter) to prevent memory leaks
     * on thread-pooled servers.
     */
    public static void clear()
    {
        SessionContext.clear();
        RequestContext.clear();
    }
}