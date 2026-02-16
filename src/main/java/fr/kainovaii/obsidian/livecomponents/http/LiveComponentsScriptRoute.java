package fr.kainovaii.obsidian.livecomponents.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Serves LiveComponents JavaScript from JAR resources.
 */
public class LiveComponentsScriptRoute implements Route
{
    private static final Logger logger = LoggerFactory.getLogger(LiveComponentsScriptRoute.class);
    private static String cachedContent;

    static {
        try {
            InputStream is = LiveComponentsScriptRoute.class.getClassLoader()
                    .getResourceAsStream("META-INF/resources/obsidian/livecomponents.js");

            if (is != null) {
                cachedContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                is.close();
                logger.info("LiveComponents script loaded successfully");
            } else {
                logger.error("LiveComponents script not found in JAR");
                cachedContent = "console.error('LiveComponents script not found');";
            }
        } catch (Exception e) {
            logger.error("Failed to load LiveComponents script", e);
            cachedContent = "console.error('Failed to load LiveComponents');";
        }
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        response.type("application/javascript; charset=utf-8");
        response.header("Cache-Control", "public, max-age=31536000");
        return cachedContent;
    }
}