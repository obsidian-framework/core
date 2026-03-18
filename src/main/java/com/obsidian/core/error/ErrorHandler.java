package com.obsidian.core.error;

import com.obsidian.core.core.EnvLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

/**
 * Global error handler for application exceptions.
 * Provides detailed debug pages in development and clean error pages in production.
 */
public class ErrorHandler
{
    /** Logger instance */
    private static final Logger logger = LoggerFactory.getLogger(ErrorHandler.class);

    /** Debug mode flag — true if ENVIRONMENT != production */
    private static boolean debugMode = !"production".equalsIgnoreCase(
            EnvLoader.getInstance().get("ENVIRONMENT", "dev")
    );

    /**
     * Sets debug mode.
     *
     * @param enabled true for debug mode (detailed errors), false for production mode
     */
    public static void setDebugMode(boolean enabled) {
        debugMode = enabled;
    }

    /**
     * Handles exception and returns appropriate error page.
     *
     * @param throwable Exception that occurred
     * @param req HTTP request
     * @param res HTTP response
     * @return HTML error page
     */
    public static String handle(Throwable throwable, Request req, Response res)
    {
        logger.error("Error occurred", throwable);

        res.status(500);
        res.type("text/html");

        if (debugMode) {
            return renderDebugPage(throwable, req);
        } else {
            return renderProductionPage();
        }
    }

    /**
     * Renders detailed debug error page with stack trace.
     *
     * @param throwable Exception that occurred
     * @param req HTTP request
     * @return HTML debug page
     */
    private static String renderDebugPage(Throwable throwable, Request req)
    {
        StackTraceElement[] stackTrace = throwable.getStackTrace();

        String exceptionClass = throwable.getClass().getName();
        String message = throwable.getMessage() != null ? throwable.getMessage() : "No message";
        String requestMethod = req.requestMethod();
        String requestPath = req.pathInfo();
        String queryString = req.queryString() != null ? "?" + req.queryString() : "";

        return generateDebugHTML(exceptionClass, message, stackTrace, requestMethod, requestPath + queryString);
    }

    /**
     * Renders simple production error page.
     *
     * @return HTML production error page
     */
    private static String renderProductionPage() {
        return generateProductionHTML();
    }

    /**
     * Generates HTML for debug error page.
     *
     * @param exceptionClass Exception class name
     * @param message Exception message
     * @param stackTrace Stack trace elements
     * @param method HTTP method
     * @param path Request path
     * @return Complete HTML page
     */
    private static String generateDebugHTML(String exceptionClass, String message, StackTraceElement[] stackTrace, String method, String path)
    {
        StringBuilder frames = new StringBuilder();
        for (int i = 0; i < stackTrace.length; i++) {
            StackTraceElement el = stackTrace[i];
            String activeClass = i == 0 ? " frame-active" : "";
            String lineNumber = el.getLineNumber() > 0
                    ? "<span class=\"frame-line\">:" + el.getLineNumber() + "</span>"
                    : "";

            frames.append("""
                    <div class="frame%s">
                        <div class="frame-header">
                            <span class="frame-number">%d</span>
                            <span class="frame-class">%s</span>
                            <span class="frame-method">%s</span>
                        </div>
                        <div class="frame-file">
                            <span>%s</span>%s
                        </div>
                    </div>
                    """.formatted(
                    activeClass, i + 1,
                    escapeHtml(el.getClassName()),
                    escapeHtml(el.getMethodName()),
                    escapeHtml(el.getFileName()),
                    lineNumber
            ));
        }

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>%s</title>
                    <link href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;600;700&display=swap" rel="stylesheet">
                    <style>%s</style>
                </head>
                <body class="noise">
                    <div class="header">
                        <div class="container">
                            <div class="exception-badge">EXCEPTION</div>
                            <h1 class="exception-class">%s</h1>
                            <p class="exception-message">%s</p>
                            <div class="request-info">
                                <span class="method-badge">%s</span>
                                <span class="path">%s</span>
                            </div>
                        </div>
                    </div>
                    <div class="container">
                        <div class="stack-trace">
                            <div class="section-title">Stack Trace</div>
                            %s
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(
                exceptionClass,
                getCSS(),
                exceptionClass,
                escapeHtml(message),
                method,
                escapeHtml(path),
                frames
        );
    }

    private static String generateProductionHTML()
    {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Server Error</title>
                    <link href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;700&display=swap" rel="stylesheet">
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body {
                            font-family: 'JetBrains Mono', monospace;
                            background: #000;
                            color: #d4d4d4;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            min-height: 100vh;
                            text-align: center;
                            padding: 2rem;
                        }
                        .error-container { max-width: 600px; }
                        .error-code { font-size: 6rem; font-weight: 700; color: #ef4444; margin-bottom: 1rem; }
                        h1 { font-size: 2rem; margin-bottom: 1rem; color: #fff; }
                        p { color: #9ca3af; line-height: 1.6; }
                    </style>
                </head>
                <body>
                    <div class="error-container">
                        <div class="error-code">500</div>
                        <h1>Internal Server Error</h1>
                        <p>Something went wrong on our end. Please try again later.</p>
                    </div>
                </body>
                </html>
                """;
    }

    /**
     * Returns CSS styles for debug error page.
     *
     * @return CSS string
     */
    private static String getCSS()
    {
        return """
            * {
                margin: 0;
                padding: 0;
                box-sizing: border-box;
            }
            
            body {
                font-family: 'JetBrains Mono', monospace;
                background: #000;
                color: #d4d4d4;
                font-size: 13px;
                line-height: 1.6;
            }
            
            .noise {
                background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 400 400' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)' opacity='0.05'/%3E%3C/svg%3E");
            }
            
            .container {
                max-width: 1200px;
                margin: 0 auto;
                padding: 0 2rem;
            }
            
            .header {
                background: #18181b;
                border-bottom: 1px solid #27272a;
                padding: 2rem 0;
                margin-bottom: 2rem;
            }
            
            .exception-badge {
                display: inline-block;
                background: #ef4444;
                color: #fff;
                padding: 0.25rem 0.75rem;
                border-radius: 4px;
                font-size: 11px;
                font-weight: 700;
                letter-spacing: 0.05em;
                margin-bottom: 1rem;
            }
            
            .exception-class {
                font-size: 2rem;
                font-weight: 700;
                color: #fff;
                margin-bottom: 0.5rem;
            }
            
            .exception-message {
                font-size: 1rem;
                color: #a1a1aa;
                margin-bottom: 1.5rem;
            }
            
            .request-info {
                display: flex;
                align-items: center;
                gap: 0.75rem;
            }
            
            .method-badge {
                background: #27272a;
                color: #a78bfa;
                padding: 0.25rem 0.5rem;
                border-radius: 4px;
                font-size: 11px;
                font-weight: 700;
            }
            
            .path {
                color: #71717a;
            }
            
            .stack-trace {
                background: #09090b;
                border: 1px solid #27272a;
                border-radius: 8px;
                overflow: hidden;
                margin-bottom: 2rem;
            }
            
            .section-title {
                background: #18181b;
                border-bottom: 1px solid #27272a;
                padding: 1rem 1.5rem;
                font-weight: 700;
                color: #fff;
                font-size: 14px;
            }
            
            .frame {
                border-bottom: 1px solid #18181b;
                padding: 1.25rem 1.5rem;
                transition: background 0.2s;
            }
            
            .frame:hover {
                background: #18181b;
            }
            
            .frame-active {
                background: #18181b;
                border-left: 3px solid #ef4444;
            }
            
            .frame-header {
                display: flex;
                align-items: center;
                gap: 0.75rem;
                margin-bottom: 0.5rem;
            }
            
            .frame-number {
                background: #27272a;
                color: #71717a;
                width: 24px;
                height: 24px;
                display: flex;
                align-items: center;
                justify-content: center;
                border-radius: 4px;
                font-size: 11px;
                font-weight: 700;
            }
            
            .frame-class {
                color: #fcd34d;
                font-weight: 600;
            }
            
            .frame-method {
                color: #34d399;
            }
            
            .frame-file {
                color: #71717a;
                font-size: 12px;
                padding-left: 2rem;
            }
            
            .frame-line {
                color: #ef4444;
                font-weight: 700;
            }
        """;
    }

    /**
     * Escapes HTML special characters to prevent XSS.
     *
     * @param text Text to escape
     * @return Escaped text
     */
    private static String escapeHtml(String text)
    {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}