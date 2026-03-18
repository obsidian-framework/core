package com.obsidian.core.livecomponents.http;

import com.obsidian.core.http.middleware.Middleware;
import spark.Request;
import spark.Response;

public class LiveComponentsScriptMiddleware implements Middleware
{
    private static final String SCRIPT_PLACEHOLDER = "</body>";

    @Override
    public void handle(Request req, Response res)
    {
        String body = res.body();
        if (body == null || !body.contains(SCRIPT_PLACEHOLDER)) return;

        String env = System.getenv("ENVIRONMENT");
        String version = "production".equalsIgnoreCase(env) ? "1.0.0" : String.valueOf(System.currentTimeMillis());
        String script = "<script src=\"/obsidian/livecomponents.js?v=" + version + "\"></script>\n";

        res.body(body.replace(SCRIPT_PLACEHOLDER, script + SCRIPT_PLACEHOLDER));
    }
}