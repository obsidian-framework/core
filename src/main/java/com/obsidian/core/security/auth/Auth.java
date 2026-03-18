package com.obsidian.core.security.auth;

import com.obsidian.core.core.Obsidian;
import com.obsidian.core.di.Container;
import com.obsidian.core.security.token.TokenResolver;
import com.obsidian.core.security.token.TokenResolverImpl;
import com.obsidian.core.security.user.UserDetails;
import com.obsidian.core.security.user.UserDetailsService;
import com.obsidian.core.security.user.UserDetailsServiceImpl;
import org.mindrot.jbcrypt.BCrypt;
import spark.Request;
import spark.Response;
import spark.Session;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.Set;

import static spark.Spark.halt;

/**
 * Static authentication utility.
 * Provides login, logout, password hashing, and user resolution helpers.
 */
public final class Auth
{
    /** Request attribute key used to cache the session-authenticated user */
    static final String CURRENT_USER_ATTR = "_current_user";

    /** Request attribute key used to cache the token-authenticated user */
    private static final String TOKEN_USER_ATTR = "_token_user";

    /** Session attribute key used to store the redirect URL after login */
    private static final String REDIRECT_AFTER_LOGIN_ATTR = "_redirect_after_login";

    /** Login page path — redirected to when authentication is required */
    private static final String LOGIN_PATH = "/login";

    /** Authorization header Bearer prefix */
    private static final String BEARER_PREFIX = "Bearer ";

    /** Singleton UserDetailsService instance */
    private static UserDetailsService userService;

    /** Singleton TokenResolver instance — null if app does not provide one */
    private static TokenResolver tokenResolver;


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
     * @param req HTTP request
     * @return true if login successful, false if credentials invalid
     * @throws LoginLockedException if the IP or username is currently locked out
     */
    public static boolean login(String username, String password, Request req)
    {
        String ip = req.ip();

        if (!LoginRateLimiter.isAllowed(ip, username)) {
            long remaining = LoginRateLimiter.getRemainingLockoutSeconds(ip);
            throw new LoginLockedException("Too many failed attempts. Try again in " + remaining + " seconds.");
        }

        UserDetails user = getUserService().loadByUsername(username);
        if (user == null || !user.isEnabled()) {
            LoginRateLimiter.recordFailure(ip, username);
            return false;
        }

        if (checkPassword(password, user.getPassword())) {
            LoginRateLimiter.recordSuccess(ip, username);

            Session oldSession = req.session(false);
            String redirectUrl = oldSession != null
                    ? (String) oldSession.attribute(REDIRECT_AFTER_LOGIN_ATTR)
                    : null;

            if (oldSession != null) oldSession.invalidate();

            Session newSession = req.session(true);
            newSession.attribute("logged", true);
            newSession.attribute("user_id", user.getId());
            newSession.attribute("username", user.getUsername());
            newSession.attribute("role", user.getRole());

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
     * @param req HTTP request
     * @param defaultUrl Fallback URL if no redirect was stored
     * @return URL to redirect to after login
     */
    public static String getRedirectAfterLogin(Request req, String defaultUrl)
    {
        Session session = req.session(false);
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
     * @param req HTTP request
     * @return true if authenticated, false otherwise
     */
    public static boolean isLogged(Request req)
    {
        Session session = req.session(false);
        if (session == null) return false;
        return Boolean.TRUE.equals(session.attribute("logged"));
    }

    /**
     * Gets currently logged in user.
     * Result is cached as a request attribute to avoid redundant DB calls.
     *
     * @param req HTTP request
     * @param <T> UserDetails type
     * @return User details or null if not logged in
     */
    @SuppressWarnings("unchecked")
    public static <T extends UserDetails> T user(Request req)
    {
        T cached = (T) req.attribute(CURRENT_USER_ATTR);
        if (cached != null) return cached;

        Session session = req.session(false);
        if (session == null) return null;

        Object userId = session.attribute("user_id");
        if (userId == null) return null;

        T user = (T) getUserService().loadById(userId);
        if (user != null) req.attribute(CURRENT_USER_ATTR, user);
        return user;
    }

    /**
     * Checks if logged in user has specific role.
     *
     * @param req HTTP request
     * @param role Role name to check
     * @return true if user has role, false otherwise
     */
    public static boolean hasRole(Request req, String role)
    {
        UserDetails u = user(req);
        return u != null && role.equals(u.getRole());
    }

    /**
     * Requires user to be logged in or redirects to login page.
     * Stores the originally requested URL in session for post-login redirect.
     *
     * @param req HTTP request
     * @param res HTTP response
     */
    public static void requireLogin(Request req, Response res)
    {
        if (!isLogged(req)) {
            req.session(true).attribute(REDIRECT_AFTER_LOGIN_ATTR, req.pathInfo());
            res.redirect(LOGIN_PATH);
            halt();
        }
    }

    /**
     * Hashes a plain text password using BCrypt.
     *
     * @param password Plain text password
     * @return BCrypt hashed password
     */
    public static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    /**
     * Verifies a plain text password against a BCrypt hash.
     *
     * @param password Plain text password
     * @param hash BCrypt hash
     * @return true if password matches hash, false otherwise
     */
    public static boolean checkPassword(String password, String hash) {
        return BCrypt.checkpw(password, hash);
    }

    /**
     * Gets or initializes TokenResolver.
     * Auto-detects implementation annotated with {@link TokenResolverImpl}.
     * Returns null if the application provides no implementation.
     *
     * @return TokenResolver instance or null
     */
    public static TokenResolver getTokenResolver()
    {
        if (tokenResolver == null) {
            try {
                tokenResolver = Container.resolve(TokenResolver.class);
            } catch (Exception e) {
                tokenResolver = autoDetectTokenResolver();
            }
        }
        return tokenResolver;
    }

    /**
     * Resolves the authenticated user from the {@code Authorization: Bearer <token>} header.
     * Result is cached as a request attribute to avoid redundant resolver calls.
     *
     * @param req HTTP request
     * @param <T> UserDetails type
     * @return User details or null if token is missing, invalid, or resolver not configured
     */
    @SuppressWarnings("unchecked")
    public static <T extends UserDetails> T userFromToken(Request req)
    {
        T cached = (T) req.attribute(TOKEN_USER_ATTR);
        if (cached != null) return cached;

        String header = req.headers("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) return null;

        String token = header.substring(BEARER_PREFIX.length()).trim();
        TokenResolver resolver = getTokenResolver();
        if (resolver == null) return null;

        T user = (T) resolver.resolve(token);
        if (user != null) req.attribute(TOKEN_USER_ATTR, user);
        return user;
    }

    /**
     * Checks if the request carries a valid Bearer token.
     *
     * @param req HTTP request
     * @return true if a valid token is present, false otherwise
     */
    public static boolean isTokenAuthenticated(Request req) {
        return userFromToken(req) != null;
    }

    /**
     * Requires valid Bearer token authentication or halts with 401 Unauthorized.
     *
     * @param req HTTP request
     * @param res HTTP response
     */
    public static void requireToken(Request req, Response res)
    {
        if (!isTokenAuthenticated(req)) {
            res.type("application/json");
            res.status(401);
            halt(401, "{\"error\":\"Unauthorized\"}");
        }
    }

    /**
     * Requires Bearer token and specific role or halts with 403 Forbidden.
     *
     * @param req HTTP request
     * @param res HTTP response
     * @param role Required role
     */
    public static void requireTokenRole(Request req, Response res, String role)
    {
        requireToken(req, res);
        UserDetails u = userFromToken(req);
        if (u == null || !role.equals(u.getRole())) {
            res.type("application/json");
            res.status(403);
            halt(403, "{\"error\":\"Forbidden\"}");
        }
    }

    /**
     * Auto-detects UserDetailsService implementation via {@link UserDetailsServiceImpl} annotation.
     *
     * @return UserDetailsService instance
     * @throws RuntimeException if no implementation found or instantiation fails
     */
    /**
     * Auto-detects UserDetailsService and TokenResolver in a single Reflections scan.
     * Populates {@link #tokenResolver} as a side effect to avoid a second scan later.
     *
     * @return UserDetailsService instance
     * @throws RuntimeException if no implementation found or instantiation fails
     */
    private static UserDetailsService autoDetectUserDetailsService()
    {
        try {
            org.reflections.Reflections reflections = new org.reflections.Reflections(Obsidian.getBasePackage());

            // Detect TokenResolver while we have the Reflections instance
            if (tokenResolver == null) {
                Set<Class<?>> tokenAnnotated = reflections.getTypesAnnotatedWith(TokenResolverImpl.class);
                if (!tokenAnnotated.isEmpty()) {
                    Class<?> implClass = tokenAnnotated.iterator().next();
                    if (TokenResolver.class.isAssignableFrom(implClass)) {
                        try {
                            tokenResolver = instantiate(implClass, TokenResolver.class);
                        } catch (Exception ignored) {}
                    }
                }
            }

            Set<Class<?>> annotated = reflections.getTypesAnnotatedWith(UserDetailsServiceImpl.class);
            if (annotated.isEmpty()) {
                throw new RuntimeException("No @UserDetailsServiceImpl found in " + Obsidian.getBasePackage());
            }

            Class<?> implClass = annotated.iterator().next();
            if (!UserDetailsService.class.isAssignableFrom(implClass)) {
                throw new RuntimeException(implClass.getName() + " does not implement UserDetailsService");
            }

            return instantiate(implClass, UserDetailsService.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to auto-detect UserDetailsService: " + e.getMessage(), e);
        }
    }

    /**
     * Auto-detects TokenResolver implementation via {@link TokenResolverImpl} annotation.
     * Only runs a new scan if not already populated by {@link #autoDetectUserDetailsService()}.
     *
     * @return TokenResolver instance or null
     */
    private static TokenResolver autoDetectTokenResolver()
    {
        try {
            org.reflections.Reflections reflections = new org.reflections.Reflections(Obsidian.getBasePackage());
            Set<Class<?>> annotated = reflections.getTypesAnnotatedWith(TokenResolverImpl.class);

            if (annotated.isEmpty()) return null;

            Class<?> implClass = annotated.iterator().next();
            if (!TokenResolver.class.isAssignableFrom(implClass)) return null;

            return instantiate(implClass, TokenResolver.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Instantiates a class using no-arg constructor or DI-resolved constructor.
     *
     * @param implClass Class to instantiate
     * @param targetType Expected type
     * @param <T> Target type
     * @return Instance of targetType
     * @throws Exception if instantiation fails
     */
    @SuppressWarnings("unchecked")
    private static <T> T instantiate(Class<?> implClass, Class<T> targetType) throws Exception
    {
        try {
            return (T) implClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            Constructor<?>[] constructors = implClass.getConstructors();
            if (constructors.length > 0) {
                Constructor<?> constructor = constructors[0];
                Parameter[] params = constructor.getParameters();
                Object[] args = new Object[params.length];
                for (int i = 0; i < params.length; i++) {
                    args[i] = Container.resolve(params[i].getType());
                }
                return (T) constructor.newInstance(args);
            }
            throw e;
        }
    }
}