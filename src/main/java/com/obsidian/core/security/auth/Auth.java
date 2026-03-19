package com.obsidian.core.security.auth;

import com.obsidian.core.di.Container;
import com.obsidian.core.di.ReflectionsProvider;
import com.obsidian.core.core.Obsidian;
import com.obsidian.core.http.RequestContext;
import com.obsidian.core.http.ResponseContext;
import com.obsidian.core.security.user.UserDetails;
import com.obsidian.core.security.user.UserDetailsService;
import com.obsidian.core.security.user.UserDetailsServiceImpl;
import com.obsidian.core.security.SessionKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Session;

import java.util.Set;

import static spark.Spark.halt;

/**
 * Session-based authentication facade.
 * Provides login, logout, and user resolution helpers.
 *
 * <p>Password utilities are in {@link AuthPassword}.
 * Token (Bearer) authentication is in {@link TokenAuth}.</p>
 */
public final class Auth
{
    private static final Logger logger = LoggerFactory.getLogger(Auth.class);

    /** Request attribute key used to cache the session-authenticated user */
    static final String CURRENT_USER_ATTR = "_current_user";

    /** Session attribute key used to store the redirect URL after login */
    private static final String REDIRECT_AFTER_LOGIN_ATTR = "_redirect_after_login";

    /** Login page path — redirected to when authentication is required */
    private static final String LOGIN_PATH = "/login";

    /** Singleton UserDetailsService instance */
    private static UserDetailsService userService;

    private Auth() {}

    /**
     * Gets or initializes UserDetailsService.
     * Auto-detects implementation annotated with {@link UserDetailsServiceImpl} if not in container.
     *
     * @return UserDetailsService instance
     * @throws RuntimeException if no implementation found
     */
    public static UserDetailsService getUserService()
    {
        if (userService == null) {
            try {
                userService = Container.resolve(UserDetailsService.class);
            } catch (Exception e) {
                userService = autoDetectUserDetailsService();
            }
        }
        return userService;
    }

    /**
     * Authenticates user and creates session.
     * Regenerates session ID to prevent session fixation attacks.
     * Enforces brute force protection via {@link LoginRateLimiter}.
     *
     * @param username Username
     * @param password Plain text password
     * @return true if login successful, false if credentials invalid
     * @throws LoginLockedException if the IP or username is currently locked out
     */
    public static boolean login(String username, String password)
    {
        String ip =  RequestContext.get().ip();

        if (!LoginRateLimiter.isAllowed(ip, username)) {
            long remaining = LoginRateLimiter.getRemainingLockoutSeconds(ip);
            throw new LoginLockedException("Too many failed attempts. Try again in " + remaining + " seconds.");
        }

        UserDetails user = getUserService().loadByUsername(username);
        if (user == null || !user.isEnabled()) {
            LoginRateLimiter.recordFailure(ip, username);
            return false;
        }

        if (AuthPassword.check(password, user.getPassword())) {
            LoginRateLimiter.recordSuccess(ip, username);

            Session oldSession = RequestContext.get().session(false);
            String redirectUrl = oldSession != null
                    ? (String) oldSession.attribute(REDIRECT_AFTER_LOGIN_ATTR)
                    : null;

            if (oldSession != null) oldSession.invalidate();

            Session newSession = RequestContext.get().session(true);
            newSession.attribute(SessionKeys.LOGGED, true);
            newSession.attribute(SessionKeys.USER_ID, user.getId());
            newSession.attribute(SessionKeys.USERNAME, user.getUsername());
            newSession.attribute(SessionKeys.ROLE, user.getRole());

            if (redirectUrl != null) {
                newSession.attribute(REDIRECT_AFTER_LOGIN_ATTR, redirectUrl);
            }

            return true;
        }

        LoginRateLimiter.recordFailure(ip, username);
        return false;
    }

    /**
     * Returns the URL the user was trying to access before being redirected to login,
     * then clears it from session. Falls back to the provided default URL.
     *
     * @param defaultUrl Fallback URL if no redirect was stored
     * @return URL to redirect to after login
     */
    public static String getRedirectAfterLogin(String defaultUrl)
    {
        Session session =  RequestContext.get().session(false);
        if (session == null) return defaultUrl;

        String url = session.attribute(REDIRECT_AFTER_LOGIN_ATTR);
        session.removeAttribute(REDIRECT_AFTER_LOGIN_ATTR);
        return url != null ? url : defaultUrl;
    }

    /**
     * Logs out user by invalidating session.
     *
     * @param session HTTP session
     */
    public static void logout(Session session) {
        if (session != null) session.invalidate();
    }

    /**
     * Checks if user is logged in.
     *
     * @return true if authenticated, false otherwise
     */
    public static boolean isLogged()
    {
        Session session = RequestContext.get().session(false);
        if (session == null) return false;
        return Boolean.TRUE.equals(session.attribute(SessionKeys.LOGGED));
    }

    /**
     * Gets currently logged in user.
     * Result is cached as a request attribute to avoid redundant DB calls.
     *
     * @param <T> UserDetails type
     * @return User details or null if not logged in
     */
    @SuppressWarnings("unchecked")
    public static <T extends UserDetails> T user()
    {
        T cached = (T) RequestContext.get().attribute(CURRENT_USER_ATTR);
        if (cached != null) return cached;

        Session session = RequestContext.get().session(false);
        if (session == null) return null;

        Object userId = session.attribute(SessionKeys.USER_ID);
        if (userId == null) return null;

        T user = (T) getUserService().loadById(userId);
        if (user != null) RequestContext.get().attribute(CURRENT_USER_ATTR, user);
        return user;
    }

    /**
     * Checks if logged-in user has specific role.
     *
     * @param role Role name to check
     * @return true if user has role, false otherwise
     */
    public static boolean hasRole(String role)
    {
        UserDetails u = user();
        return u != null && role.equals(u.getRole());
    }

    /**
     * Requires user to be logged in or redirects to login page.
     * Stores the originally requested URL in session for post-login redirect.
     */
    public static void requireLogin()
    {
        if (!isLogged()) {
            RequestContext.get().session(true).attribute(REDIRECT_AFTER_LOGIN_ATTR, RequestContext.get().pathInfo());
            ResponseContext.get().redirect(LOGIN_PATH);
            halt();
        }
    }

    /**
     * Hashes a plain text password using BCrypt.
     *
     * @param password Plain text password
     * @return BCrypt hashed password
     * @deprecated Use {@link AuthPassword#hash(String)} directly
     */
    @Deprecated
    public static String hashPassword(String password) {
        return AuthPassword.hash(password);
    }

    /**
     * Verifies a plain text password against a BCrypt hash.
     *
     * @param password Plain text password
     * @param hash BCrypt hash
     * @return true if password matches hash, false otherwise
     * @deprecated Use {@link AuthPassword#check(String, String)} directly
     */
    @Deprecated
    public static boolean checkPassword(String password, String hash) {
        return AuthPassword.check(password, hash);
    }

    /**
     * Auto-detects UserDetailsService implementation via {@link UserDetailsServiceImpl} annotation.
     *
     * @return UserDetailsService instance
     * @throws RuntimeException if no implementation found or instantiation fails
     */
    private static UserDetailsService autoDetectUserDetailsService()
    {
        try {
            Set<Class<?>> annotated = ReflectionsProvider.getTypesAnnotatedWith(UserDetailsServiceImpl.class);
            if (annotated.isEmpty()) {
                throw new RuntimeException("No @UserDetailsServiceImpl found in " + Obsidian.getBasePackage());
            }

            Class<?> implClass = annotated.iterator().next();
            if (!UserDetailsService.class.isAssignableFrom(implClass)) {
                throw new RuntimeException(implClass.getName() + " does not implement UserDetailsService");
            }

            return Container.instantiate(implClass, UserDetailsService.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to auto-detect UserDetailsService: " + e.getMessage(), e);
        }
    }
}