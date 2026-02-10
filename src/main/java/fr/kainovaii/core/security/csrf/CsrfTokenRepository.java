package fr.kainovaii.core.security.csrf;

public interface CsrfTokenRepository
{
    CsrfToken generateToken(String sessionId);
    CsrfToken getToken(String sessionId);
    boolean validateToken(String sessionId, String token);
    void removeToken(String sessionId);
}