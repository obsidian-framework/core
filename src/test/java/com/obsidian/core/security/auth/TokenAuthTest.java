package com.obsidian.core.security.auth;

import com.obsidian.core.security.token.TokenResolver;
import com.obsidian.core.security.user.UserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import spark.Request;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TokenAuthTest
{
    private Request req;
    private TokenResolver tokenResolver;

    /** Simulated request attributes store */
    private Map<String, Object> requestAttrs;

    @BeforeEach
    void setUp() throws Exception {
        // Inject a mock TokenResolver to avoid Reflections auto-detect
        tokenResolver = mock(TokenResolver.class);
        Field f = TokenAuth.class.getDeclaredField("tokenResolver");
        f.setAccessible(true);
        f.set(null, tokenResolver);

        // Setup mock request with attribute storage
        requestAttrs = new HashMap<>();
        req = mock(Request.class);
        doAnswer(inv -> requestAttrs.put(inv.getArgument(0), inv.getArgument(1)))
                .when(req).attribute(anyString(), any());
        doAnswer(inv -> requestAttrs.get(inv.<String>getArgument(0)))
                .when(req).attribute(anyString());
    }

    // ──────────────────────────────────────────────
    // userFromToken
    // ──────────────────────────────────────────────

    @Test
    void userFromToken_noHeader_returnsNull() {
        when(req.headers("Authorization")).thenReturn(null);

        assertNull(TokenAuth.userFromToken(req));
    }

    @Test
    void userFromToken_invalidPrefix_returnsNull() {
        when(req.headers("Authorization")).thenReturn("Basic abc123");

        assertNull(TokenAuth.userFromToken(req));
    }

    @Test
    void userFromToken_emptyBearer_returnsNull() {
        when(req.headers("Authorization")).thenReturn("Bearer ");
        when(tokenResolver.resolve("")).thenReturn(null);

        assertNull(TokenAuth.userFromToken(req));
    }

    @Test
    void userFromToken_validToken_returnsUser() {
        UserDetails user = mock(UserDetails.class);
        when(req.headers("Authorization")).thenReturn("Bearer valid-token-123");
        when(tokenResolver.resolve("valid-token-123")).thenReturn(user);

        UserDetails result = TokenAuth.userFromToken(req);

        assertNotNull(result);
        assertSame(user, result);
    }

    @Test
    void userFromToken_cachesResultInRequestAttribute() {
        UserDetails user = mock(UserDetails.class);
        when(req.headers("Authorization")).thenReturn("Bearer cached-token");
        when(tokenResolver.resolve("cached-token")).thenReturn(user);

        TokenAuth.userFromToken(req);
        TokenAuth.userFromToken(req);

        // resolve should only be called once — second call uses cached attribute
        verify(tokenResolver, times(1)).resolve("cached-token");
    }

    // ──────────────────────────────────────────────
    // isAuthenticated
    // ──────────────────────────────────────────────

    @Test
    void isAuthenticated_noToken_returnsFalse() {
        when(req.headers("Authorization")).thenReturn(null);

        assertFalse(TokenAuth.isAuthenticated(req));
    }

    @Test
    void isAuthenticated_invalidToken_returnsFalse() {
        when(req.headers("Authorization")).thenReturn("Basic abc123");

        assertFalse(TokenAuth.isAuthenticated(req));
    }

    @Test
    void isAuthenticated_validToken_returnsTrue() {
        UserDetails user = mock(UserDetails.class);
        when(req.headers("Authorization")).thenReturn("Bearer good-token");
        when(tokenResolver.resolve("good-token")).thenReturn(user);

        assertTrue(TokenAuth.isAuthenticated(req));
    }
}