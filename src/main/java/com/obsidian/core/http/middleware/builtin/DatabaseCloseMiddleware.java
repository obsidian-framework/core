package com.obsidian.core.http.middleware.builtin;

import com.obsidian.core.http.middleware.Middleware;
import org.javalite.activejdbc.Base;
import spark.Request;
import spark.Response;

/**
 * Built-in middleware that closes the database connection after route execution.
 * Registered automatically by the framework on all routes — no user configuration required.
 *
 * <p>Only closes the connection if one is active on the current thread,
 * preventing errors on routes that did not interact with the database.</p>
 *
 * @see DatabaseMiddleware
 */
public class DatabaseCloseMiddleware implements Middleware
{
    /**
     * Closes the database connection on the current thread if open.
     *
     * @param req HTTP request
     * @param res HTTP response
     */
    @Override
    public void handle(Request req, Response res)
    {
        if (Base.hasConnection()) {
            Base.close();
        }
    }
}