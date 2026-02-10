package fr.kainovaii.core.security.csrf;

import spark.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CsrfProtection
{
    private static final Logger logger = LoggerFactory.getLogger(CsrfProtection.class);
    private static CsrfTokenRepository repository = new InMemoryCsrfTokenRepository();

    public static void setRepository(CsrfTokenRepository repo)
    {
        repository = repo;
    }

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