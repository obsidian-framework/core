package com.obsidian.core.security.auth;

import com.obsidian.core.di.Container;
import com.obsidian.core.di.ReflectionsProvider;
import com.obsidian.core.http.RequestContext;
import com.obsidian.core.http.ResponseContext;
import com.obsidian.core.security.token.TokenResolver;
import com.obsidian.core.security.token.TokenResolverImpl;
import com.obsidian.core.security.user.UserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * @param <T> UserDetails type
     * @return User details or null if token is missing, invalid, or resolver not configured
     */
    @SuppressWarnings("unchecked")
    public static <T extends UserDetails> T userFromToken()
    {
        T cached = (T) RequestContext.get().attribute(TOKEN_USER_ATTR);
        if (cached != null) return cached;

        String header = RequestContext.get().headers("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) return null;

        String token = header.substring(BEARER_PREFIX.length()).trim();
        TokenResolver resolver = getTokenResolver();
        if (resolver == null) return null;

        T user = (T) resolver.resolve(token);
        if (user != null) RequestContext.get().attribute(TOKEN_USER_ATTR, user);
        return user;
    }

    /**
     * Checks if the request carries a valid Bearer token.
     *
     * @return true if a valid token is present, false otherwise
     */
    public static boolean isAuthenticated() {
        return userFromToken() != null;
    }

    /**
     * Requires valid Bearer token authentication or halts with 401 Unauthorized.
     */
    public static void requireToken()
    {
        if (!isAuthenticated()) {
            ResponseContext.get().type("application/json");
            ResponseContext.get().status(401);
            halt(401, "{\"error\":\"Unauthorized\"}");
        }
    }

    /**
     * Requires Bearer token and specific role or halts with 403 Forbidden.
     *
     * @param role Required role
     */
    public static void requireTokenRole(String role)
    {
        requireToken();
        UserDetails u = userFromToken();
        if (u == null || !role.equals(u.getRole())) {
            ResponseContext.get().type("application/json");
            ResponseContext.get().status(403);
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
            Set<Class<?>> annotated = ReflectionsProvider.getTypesAnnotatedWith(TokenResolverImpl.class);

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