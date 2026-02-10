package fr.kainovaii.core.security.csrf;

import java.time.Instant;
import java.util.UUID;

public class CsrfToken
{
    private final String token;
    private final Instant createdAt;
    private final Instant expiresAt;

    public CsrfToken()
    {
        this.token = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.expiresAt = createdAt.plusSeconds(3600); // 1 heure
    }

    public CsrfToken(String token, Instant createdAt, Instant expiresAt)
    {
        this.token = token;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getToken() {
        return token;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}