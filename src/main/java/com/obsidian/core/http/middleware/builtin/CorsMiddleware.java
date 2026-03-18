package com.obsidian.core.http.middleware.builtin;

import com.obsidian.core.http.middleware.Middleware;
import spark.Request;
import spark.Response;

public class CorsMiddleware implements Middleware
{
    @Override
    public void handle(Request req, Response res) {
        res.header("Access-Control-Allow-Origin", "*");
        res.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        res.header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-CSRF-TOKEN");
        res.header("Access-Control-Max-Age", "3600");
    }
}
