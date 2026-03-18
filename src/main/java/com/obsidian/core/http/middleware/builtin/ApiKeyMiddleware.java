package com.obsidian.core.http.middleware.builtin;

import com.obsidian.core.http.middleware.Middleware;
import spark.Request;
import spark.Response;

import static spark.Spark.halt;

public class ApiKeyMiddleware implements Middleware
{
    private static final String VALID_API_KEY = "your-secret-api-key";

    @Override
    public void handle(Request req, Response res) {
        String apiKey = req.headers("X-API-Key");
        
        if (apiKey == null || !apiKey.equals(VALID_API_KEY)) {
            res.type("application/json");
            halt(401, "{\"error\":\"Invalid or missing API key\"}");
        }
    }
}
