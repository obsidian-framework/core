package com.obsidian.core.security.csrf;

/**
 * Repository interface for CSRF token storage and management.
 * Implementations can store tokens in memory, database, Redis, etc.
 */
public interface CsrfTokenRepository
{
    /**
     * Generates and stores new CSRF token for session.
     *
     * @param sessionId Session identifier
     * @return Generated CSRF token
     */
    CsrfToken generateToken(String sessionId);

    /**
     * Retrieves CSRF token for session.
     *
     * @param sessionId Session identifier
     * @return CSRF token or null if not found or expired
     */
    CsrfToken getToken(String sessionId);

    /**
     * Validates token for session.
     *
     * @param sessionId Session identifier
     * @param token Token to validate
     * @return true if token is valid, false otherwise
     */
    boolean validateToken(String sessionId, String token);

    /**
     * Removes token for session.
     *
     * @param sessionId Session identifier
     */
    void removeToken(String sessionId);
}