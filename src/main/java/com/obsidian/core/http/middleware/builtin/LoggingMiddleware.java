package com.obsidian.core.http.middleware.builtin;

import com.obsidian.core.http.middleware.Middleware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class LoggingMiddleware implements Middleware
{
    private static final Logger logger = LoggerFactory.getLogger(LoggingMiddleware.class);

    @Override
    public void handle(Request req, Response res) {
        logger.info("{} {} - IP: {} - User-Agent: {}", 
            req.requestMethod(), 
            req.pathInfo(), 
            req.ip(),
            req.userAgent());
    }
}
