package com.obsidian.core.security.csrf;

import java.time.Instant;
import java.util.UUID;

/**
 * CSRF token with expiration.
 * Represents a single-use or time-limited token for CSRF protection.
 */
public class CsrfToken
{
    /** Token value */
    private final String token;

    /** Creation timestamp */
    private final Instant createdAt;

    /** Expiration timestamp */
    private final Instant expiresAt;

    /**
     * Constructor - generates new token.
     * Token expires after 1 hour.
     */
    public CsrfToken()
    {
        this.token = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.expiresAt = createdAt.plusSeconds(3600);
    }

    /**
     * Constructor with explicit values.
     *
     * @param token Token value
     * @param createdAt Creation timestamp
     * @param expiresAt Expiration timestamp
     */
    public CsrfToken(String token, Instant createdAt, Instant expiresAt)
    {
        this.token = token;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /**
     * Gets token value.
     *
     * @return Token string
     */
    public String getToken() {
        return token;
    }

    /**
     * Checks if token is expired.
     *
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Gets creation timestamp.
     *
     * @return Creation instant
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Gets expiration timestamp.
     *
     * @return Expiration instant
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }
}