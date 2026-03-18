package com.obsidian.core.http.middleware.builtin;

import com.obsidian.core.http.middleware.Middleware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class TimingMiddleware implements Middleware
{
    private static final Logger logger = LoggerFactory.getLogger(TimingMiddleware.class);
    private static final String START_TIME_ATTR = "request.start.time";

    @Override
    public void handle(Request req, Response res)
    {
        Long startTime = req.attribute(START_TIME_ATTR);
        
        if (startTime == null) {
            req.attribute(START_TIME_ATTR, System.currentTimeMillis());
        } else {
            long duration = System.currentTimeMillis() - startTime;
            res.header("X-Response-Time", duration + "ms");
            logger.info("Request completed in {}ms - {} {}", duration, req.requestMethod(), req.pathInfo());
        }
    }
}
