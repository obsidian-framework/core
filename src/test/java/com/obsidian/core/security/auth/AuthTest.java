package com.obsidian.core.security.auth;

import com.obsidian.core.security.SessionKeys;
import com.obsidian.core.security.user.UserDetails;
import com.obsidian.core.security.user.UserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import spark.Request;
import spark.Session;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthTest
{
    private UserDetailsService userService;
    private Request req;
    private Session session;

    /** Simulated session attributes store */
    private Map<String, Object> sessionAttrs;

    /** Simulated request attributes store */
    private Map<String, Object> requestAttrs;

    @BeforeEach
    void setUp() throws Exception {
        // Reset Auth static state
        setStaticField(Auth.class, "userService", null);

        // Reset rate limiter
        resetRateLimiter();

        // Setup fake UserDetailsService
        userService = mock(UserDetailsService.class);
        setStaticField(Auth.class, "userService", userService);

        // Setup mock session with attribute storage
        sessionAttrs = new HashMap<>();
        session = mock(Session.class);
        doAnswer(inv -> sessionAttrs.put(inv.getArgument(0), inv.getArgument(1)))
                .when(session).attribute(anyString(), any());
        doAnswer(inv -> sessionAttrs.get(inv.<String>getArgument(0)))
                .when(session).attribute(anyString());
        doAnswer(inv -> { sessionAttrs.clear(); return null; })
                .when(session).invalidate();
        doAnswer(inv -> { sessionAttrs.remove(inv.<String>getArgument(0)); return null; })
                .when(session).removeAttribute(anyString());

        // Setup mock request with attribute storage
        requestAttrs = new HashMap<>();
        req = mock(Request.class);
        when(req.session(false)).thenReturn(session);
        when(req.session(true)).thenReturn(session);
        when(req.session()).thenReturn(session);
        when(req.ip()).thenReturn("127.0.0.1");
        doAnswer(inv -> requestAttrs.put(inv.getArgument(0), inv.getArgument(1)))
                .when(req).attribute(anyString(), any());
        doAnswer(inv -> requestAttrs.get(inv.<String>getArgument(0)))
                .when(req).attribute(anyString());
    }

    private void setStaticField(Class<?> clazz, String name, Object value) throws Exception {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }

    private void resetRateLimiter() throws Exception {
        for (String fieldName : new String[]{"ipCounters", "usernameCounters"}) {
            Field f = LoginRateLimiter.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            ((Map<?, ?>) f.get(null)).clear();
        }
    }

    private UserDetails fakeUser(Object id, String username, String password, String role) {
        String hashed = AuthPassword.hash(password);
        UserDetails user = mock(UserDetails.class);
        when(user.getId()).thenReturn(id);
        when(user.getUsername()).thenReturn(username);
        when(user.getPassword()).thenReturn(hashed);
        when(user.getRole()).thenReturn(role);
        when(user.isEnabled()).thenReturn(true);
        return user;
    }

    // ──────────────────────────────────────────────
    // login
    // ──────────────────────────────────────────────

    @Test
    void login_validCredentials_returnsTrue() {
        UserDetails user = fakeUser(1, "admin", "secret", "ADMIN");
        when(userService.loadByUsername("admin")).thenReturn(user);

        assertTrue(Auth.login("admin", "secret", req));
    }

    @Test
    void login_wrongPassword_returnsFalse() {
        UserDetails user = fakeUser(1, "admin", "secret", "ADMIN");
        when(userService.loadByUsername("admin")).thenReturn(user);

        assertFalse(Auth.login("admin", "wrong", req));
    }

    @Test
    void login_unknownUser_returnsFalse() {
        when(userService.loadByUsername("unknown")).thenReturn(null);

        assertFalse(Auth.login("unknown", "whatever", req));
    }

    @Test
    void login_disabledUser_returnsFalse() {
        UserDetails user = fakeUser(1, "disabled", "secret", "USER");
        when(user.isEnabled()).thenReturn(false);
        when(userService.loadByUsername("disabled")).thenReturn(user);

        assertFalse(Auth.login("disabled", "secret", req));
    }

    @Test
    void login_success_setsSessionAttributes() {
        UserDetails user = fakeUser(1, "admin", "secret", "ADMIN");
        when(userService.loadByUsername("admin")).thenReturn(user);

        Auth.login("admin", "secret", req);

        assertEquals(true, sessionAttrs.get(SessionKeys.LOGGED));
        assertEquals(1, sessionAttrs.get(SessionKeys.USER_ID));
        assertEquals("admin", sessionAttrs.get(SessionKeys.USERNAME));
        assertEquals("ADMIN", sessionAttrs.get(SessionKeys.ROLE));
    }

    // ──────────────────────────────────────────────
    // isLogged
    // ──────────────────────────────────────────────

    @Test
    void isLogged_noSession_returnsFalse() {
        when(req.session(false)).thenReturn(null);

        assertFalse(Auth.isLogged(req));
    }

    @Test
    void isLogged_sessionWithLogged_returnsTrue() {
        sessionAttrs.put(SessionKeys.LOGGED, true);

        assertTrue(Auth.isLogged(req));
    }

    @Test
    void isLogged_sessionWithoutLogged_returnsFalse() {
        assertFalse(Auth.isLogged(req));
    }

    // ──────────────────────────────────────────────
    // logout
    // ──────────────────────────────────────────────

    @Test
    void logout_invalidatesSession() {
        Auth.logout(session);

        verify(session).invalidate();
    }

    @Test
    void logout_nullSession_doesNotThrow() {
        assertDoesNotThrow(() -> Auth.logout(null));
    }

    // ──────────────────────────────────────────────
    // user
    // ──────────────────────────────────────────────

    @Test
    void user_loggedIn_returnsUserDetails() {
        UserDetails user = fakeUser(42, "bob", "pass", "USER");
        sessionAttrs.put(SessionKeys.USER_ID, 42);
        when(userService.loadById(42)).thenReturn(user);

        UserDetails result = Auth.user(req);

        assertNotNull(result);
        assertEquals("bob", result.getUsername());
    }

    @Test
    void user_noSession_returnsNull() {
        when(req.session(false)).thenReturn(null);

        assertNull(Auth.user(req));
    }

    @Test
    void user_noUserId_returnsNull() {
        assertNull(Auth.user(req));
    }

    @Test
    void user_cachesResultInRequestAttribute() {
        UserDetails user = fakeUser(42, "bob", "pass", "USER");
        sessionAttrs.put(SessionKeys.USER_ID, 42);
        when(userService.loadById(42)).thenReturn(user);

        Auth.user(req);
        Auth.user(req);

        verify(userService, times(1)).loadById(42);
    }

    // ──────────────────────────────────────────────
    // hasRole
    // ──────────────────────────────────────────────

    @Test
    void hasRole_matchingRole_returnsTrue() {
        UserDetails user = fakeUser(1, "admin", "pass", "ADMIN");
        sessionAttrs.put(SessionKeys.USER_ID, 1);
        when(userService.loadById(1)).thenReturn(user);

        assertTrue(Auth.hasRole(req, "ADMIN"));
    }

    @Test
    void hasRole_differentRole_returnsFalse() {
        UserDetails user = fakeUser(1, "admin", "pass", "USER");
        sessionAttrs.put(SessionKeys.USER_ID, 1);
        when(userService.loadById(1)).thenReturn(user);

        assertFalse(Auth.hasRole(req, "ADMIN"));
    }

    @Test
    void hasRole_notLoggedIn_returnsFalse() {
        when(req.session(false)).thenReturn(null);

        assertFalse(Auth.hasRole(req, "ADMIN"));
    }

    // ──────────────────────────────────────────────
    // getRedirectAfterLogin
    // ──────────────────────────────────────────────

    @Test
    void getRedirectAfterLogin_storedUrl_returnsIt() {
        sessionAttrs.put("_redirect_after_login", "/dashboard");

        String url = Auth.getRedirectAfterLogin(req, "/home");

        assertEquals("/dashboard", url);
    }

    @Test
    void getRedirectAfterLogin_noUrl_returnsDefault() {
        String url = Auth.getRedirectAfterLogin(req, "/home");

        assertEquals("/home", url);
    }

    @Test
    void getRedirectAfterLogin_noSession_returnsDefault() {
        when(req.session(false)).thenReturn(null);

        String url = Auth.getRedirectAfterLogin(req, "/home");

        assertEquals("/home", url);
    }

    @Test
    void getRedirectAfterLogin_clearsUrlAfterRetrieving() {
        sessionAttrs.put("_redirect_after_login", "/dashboard");

        Auth.getRedirectAfterLogin(req, "/home");

        verify(session).removeAttribute("_redirect_after_login");
    }

    // ──────────────────────────────────────────────
    // Brute force integration
    // ──────────────────────────────────────────────

    @Test
    void login_afterFiveFails_throwsLockedException() {
        when(userService.loadByUsername("admin")).thenReturn(null);

        for (int i = 0; i < 5; i++) {
            Auth.login("admin", "wrong", req);
        }

        assertThrows(LoginLockedException.class,
                () -> Auth.login("admin", "wrong", req));
    }
}