package com.obsidian.core.http.middleware.builtin;

import com.obsidian.core.database.DB;
import com.obsidian.core.http.middleware.Middleware;
import org.javalite.activejdbc.Base;
import spark.Request;
import spark.Response;

/**
 * Built-in middleware that opens a database connection before route execution.
 * Registered automatically by the framework on all routes — no user configuration required.
 *
 * <p>Uses a thread-local connection as required by ActiveJDBC.
 * The connection is only opened if none is already active on the current thread.</p>
 *
 * @see DatabaseCloseMiddleware
 */
public class DatabaseMiddleware implements Middleware
{
    /**
     * Opens a database connection on the current thread if not already open.
     *
     * @param req HTTP request
     * @param res HTTP response
     * @throws Exception if the connection cannot be opened
     */
    @Override
    public void handle(Request req, Response res) throws Exception
    {
        if (!Base.hasConnection()) {
            DB.getInstance().connect();
        }
    }
}