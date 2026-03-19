package com.obsidian.core.security.auth;

import com.obsidian.core.core.Obsidian;
import com.obsidian.core.di.Container;
import com.obsidian.core.security.token.TokenResolver;
import com.obsidian.core.security.token.TokenResolverImpl;
import com.obsidian.core.security.user.UserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.Set;

import static spark.Spark.halt;

/**
 * Bearer token authentication.
 * Resolves users from {@code Authorization: Bearer <token>} headers
 * using a {@link TokenResolver} implementation.
 */
public final class TokenAuth
{
    private static final Logger logger = LoggerFactory.getLogger(TokenAuth.class);

    /** Request attribute key used to cache the token-authenticated user */
    private static final String TOKEN_USER_ATTR = "_token_user";

    /** Authorization header Bearer prefix */
    private static final String BEARER_PREFIX = "Bearer ";

    /** Singleton TokenResolver instance — null if app does not provide one */
    private static TokenResolver tokenResolver;

    private TokenAuth() {}

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
    public static boolean isAuthenticated(Request req) {
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
        if (!isAuthenticated(req)) {
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
     * Auto-detects TokenResolver implementation via {@link TokenResolverImpl} annotation.
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

            return Container.instantiate(implClass, TokenResolver.class);
        } catch (Exception e) {
            logger.warn("Failed to auto-detect TokenResolver", e);
            return null;
        }
    }
}