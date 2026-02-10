package fr.kainovaii.core.web.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class ErrorHandler
{
    private static final Logger logger = LoggerFactory.getLogger(ErrorHandler.class);
    private static boolean debugMode = true;

    public static void setDebugMode(boolean enabled) { debugMode = enabled; }

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

    private static String renderProductionPage() {
        return generateProductionHTML();
    }

    private static String generateDebugHTML(String exceptionClass, String message, StackTraceElement[] stackTrace, String method, String path)
    {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>").append(exceptionClass).append("</title>\n");
        html.append("    <link href=\"https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;600;700&display=swap\" rel=\"stylesheet\">\n");
        html.append("    <style>\n");
        html.append(getCSS());
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body class=\"noise\">\n");

        // Header
        html.append("    <div class=\"header\">\n");
        html.append("        <div class=\"container\">\n");
        html.append("            <div class=\"exception-badge\">EXCEPTION</div>\n");
        html.append("            <h1 class=\"exception-class\">").append(exceptionClass).append("</h1>\n");
        html.append("            <p class=\"exception-message\">").append(escapeHtml(message)).append("</p>\n");
        html.append("            <div class=\"request-info\">\n");
        html.append("                <span class=\"method-badge\">").append(method).append("</span>\n");
        html.append("                <span class=\"path\">").append(escapeHtml(path)).append("</span>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");
        html.append("    </div>\n");

        // Stack trace
        html.append("    <div class=\"container\">\n");
        html.append("        <div class=\"stack-trace\">\n");
        html.append("            <div class=\"section-title\">Stack Trace</div>\n");

        for (int i = 0; i < stackTrace.length; i++) {
            StackTraceElement element = stackTrace[i];
            boolean isFirst = i == 0;

            html.append("            <div class=\"frame").append(isFirst ? " frame-active" : "").append("\">\n");
            html.append("                <div class=\"frame-header\">\n");
            html.append("                    <span class=\"frame-number\">").append(i + 1).append("</span>\n");
            html.append("                    <span class=\"frame-class\">").append(escapeHtml(element.getClassName())).append("</span>\n");
            html.append("                    <span class=\"frame-method\">").append(escapeHtml(element.getMethodName())).append("</span>\n");
            html.append("                </div>\n");
            html.append("                <div class=\"frame-file\">\n");
            html.append("                    <span>").append(escapeHtml(element.getFileName())).append("</span>\n");
            if (element.getLineNumber() > 0) {
                html.append("                    <span class=\"frame-line\">:").append(element.getLineNumber()).append("</span>\n");
            }
            html.append("                </div>\n");
            html.append("            </div>\n");
        }

        html.append("        </div>\n");
        html.append("    </div>\n");

        html.append("</body>\n");
        html.append("</html>");

        return html.toString();
    }

    private static String generateProductionHTML()
    {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>Server Error</title>\n" +
                "    <link href=\"https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;700&display=swap\" rel=\"stylesheet\">\n" +
                "    <style>\n" +
                "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
                "        body {\n" +
                "            font-family: 'JetBrains Mono', monospace;\n" +
                "            background: #000;\n" +
                "            color: #d4d4d4;\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            justify-content: center;\n" +
                "            min-height: 100vh;\n" +
                "            text-align: center;\n" +
                "            padding: 2rem;\n" +
                "        }\n" +
                "        .error-container { max-width: 600px; }\n" +
                "        .error-code { font-size: 6rem; font-weight: 700; color: #ef4444; margin-bottom: 1rem; }\n" +
                "        h1 { font-size: 2rem; margin-bottom: 1rem; color: #fff; }\n" +
                "        p { color: #9ca3af; line-height: 1.6; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"error-container\">\n" +
                "        <div class=\"error-code\">500</div>\n" +
                "        <h1>Internal Server Error</h1>\n" +
                "        <p>Something went wrong on our end. Please try again later.</p>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
    }

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