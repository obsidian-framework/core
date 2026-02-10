package fr.kainovaii.core.security.csrf;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryCsrfTokenRepository implements CsrfTokenRepository
{
    private final Map<String, CsrfToken> tokens = new ConcurrentHashMap<>();

    @Override
    public CsrfToken generateToken(String sessionId)
    {
        CsrfToken token = new CsrfToken();
        tokens.put(sessionId, token);
        return token;
    }

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

    @Override
    public boolean validateToken(String sessionId, String token) {

        CsrfToken storedToken = getToken(sessionId);
        if (storedToken == null) {
            return false;
        }
        return storedToken.getToken().equals(token);
    }

    @Override
    public void removeToken(String sessionId) {
        tokens.remove(sessionId);
    }
}