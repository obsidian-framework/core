package com.obsidian.core.http.middleware.builtin;

import com.obsidian.core.http.middleware.Middleware;
import spark.Request;
import spark.Response;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static spark.Spark.halt;

public class RateLimitMiddleware implements Middleware
{
    private static final int MAX_REQUESTS = 100;
    private static final long TIME_WINDOW = 60000;
    
    private static final Map<String, RequestCounter> counters = new ConcurrentHashMap<>();

    @Override
    public void handle(Request req, Response res)
    {
        String ip = req.ip();
        RequestCounter counter = counters.computeIfAbsent(ip, k -> new RequestCounter());

        if (counter.increment()) {
            res.header("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS));
            res.header("X-RateLimit-Remaining", String.valueOf(counter.remaining()));
        } else {
            res.status(429);
            halt(429, "Too many requests. Please try again later.");
        }
    }

    private static class RequestCounter
    {
        private final AtomicInteger count = new AtomicInteger(0);
        private long windowStart = System.currentTimeMillis();

        public synchronized boolean increment() {
            long now = System.currentTimeMillis();
            
            if (now - windowStart > TIME_WINDOW) {
                count.set(0);
                windowStart = now;
            }

            return count.incrementAndGet() <= MAX_REQUESTS;
        }

        public int remaining() {
            return Math.max(0, MAX_REQUESTS - count.get());
        }
    }
}
