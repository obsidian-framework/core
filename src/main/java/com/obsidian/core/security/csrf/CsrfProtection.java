package com.obsidian.core.security.csrf;

import spark.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CSRF (Cross-Site Request Forgery) protection system.
 * Manages token generation, validation and session binding.
 */
public class CsrfProtection
{
    /** Logger instance */
    private static final Logger logger = LoggerFactory.getLogger(CsrfProtection.class);

    /** Token repository (default: in-memory) */
    private static CsrfTokenRepository repository = new InMemoryCsrfTokenRepository();

    /**
     * Sets custom token repository.
     *
     * @param repo Token repository implementation
     */
    public static void setRepository(CsrfTokenRepository repo)
    {
        repository = repo;
    }

    /**
     * Gets or generates CSRF token for request session.
     *
     * @param req HTTP request
     * @return CSRF token string
     */
    public static String getToken(Request req)
    {
        if (req.session(false) == null) {
            req.session(true);
        }

        String sessionId = req.session().id();
        CsrfToken token = repository.getToken(sessionId);

        if (token == null) {
            token = repository.generateToken(sessionId);
            logger.debug("Generated new CSRF token for session: {}", sessionId);
        }

        return token.getToken();
    }

    /**
     * Validates CSRF token from request.
     * Checks X-CSRF-TOKEN header or _csrf query/form parameter.
     *
     * @param req HTTP request
     * @return true if token is valid, false otherwise
     */
    public static boolean validate(Request req)
    {
        if (req.session(false) == null) {
            logger.warn("CSRF validation failed: No session");
            return false;
        }

        String sessionId = req.session().id();

        String token = req.headers("X-CSRF-TOKEN");
        if (token == null) {
            token = req.queryParams("_csrf");
        }

        if (token == null) {
            logger.warn("CSRF validation failed: No token provided");
            return false;
        }

        boolean isValid = repository.validateToken(sessionId, token);

        if (!isValid) {
            logger.warn("CSRF validation failed for session: {}", sessionId);
        }

        return isValid;
    }

    /**
     * Regenerates CSRF token for session.
     * Useful after login or sensitive operations.
     *
     * @param req HTTP request
     */
    public static void regenerateToken(Request req)
    {
        if (req.session(false) == null) {
            return;
        }
        String sessionId = req.session().id();
        repository.removeToken(sessionId);
        repository.generateToken(sessionId);
        logger.debug("Regenerated CSRF token for session: {}", sessionId);
    }
}