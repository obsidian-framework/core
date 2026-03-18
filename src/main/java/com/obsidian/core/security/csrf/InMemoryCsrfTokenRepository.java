package com.obsidian.core.security.csrf;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of CSRF token repository.
 * Stores tokens in a concurrent hash map for thread-safe access.
 */
public class InMemoryCsrfTokenRepository implements CsrfTokenRepository
{
    /** Token storage map (sessionId -> token) */
    private final Map<String, CsrfToken> tokens = new ConcurrentHashMap<>();

    /**
     * Generates and stores new CSRF token.
     *
     * @param sessionId Session identifier
     * @return Generated token
     */
    @Override
    public CsrfToken generateToken(String sessionId)
    {
        CsrfToken token = new CsrfToken();
        tokens.put(sessionId, token);
        return token;
    }

    /**
     * Gets token for session, removing if expired.
     *
     * @param sessionId Session identifier
     * @return Token or null if not found or expired
     */
    @Override
    public CsrfToken getToken(String sessionId)
    {
        CsrfToken token = tokens.get(sessionId);
        if (token != null && token.isExpired()) {
            tokens.remove(sessionId);
            return null;
        }
        return token;
    }

    /**
     * Validates token matches stored token for session.
     *
     * @param sessionId Session identifier
     * @param token Token to validate
     * @return true if valid, false otherwise
     */
    @Override
    public boolean validateToken(String sessionId, String token)
    {
        CsrfToken storedToken = getToken(sessionId);
        if (storedToken == null) {
            return false;
        }
        return storedToken.getToken().equals(token);
    }

    /**
     * Removes token for session.
     *
     * @param sessionId Session identifier
     */
    @Override
    public void removeToken(String sessionId) {
        tokens.remove(sessionId);
    }
}