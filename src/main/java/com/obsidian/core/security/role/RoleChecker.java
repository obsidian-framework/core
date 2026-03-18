package com.obsidian.core.security.role;

import com.obsidian.core.core.Obsidian;
import com.obsidian.core.security.auth.Auth;
import com.obsidian.core.security.user.UserDetails;
import spark.Request;
import spark.Response;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static spark.Spark.halt;

/**
 * Role-based access control checker.
 * Manages route-to-role mappings and login-required routes.
 */
public class RoleChecker
{
    /** Map of route patterns to required roles */
    private static final Map<String, String> pathToRole = new HashMap<>();

    /** Set of route patterns that require authentication (session) */
    private static final Set<String> loginRequiredPaths = new HashSet<>();

    /** Set of route patterns that require Bearer token authentication */
    private static final Set<String> tokenRequiredPaths = new HashSet<>();

    /** Map of route patterns to required roles (token auth) */
    private static final Map<String, String> tokenPathToRole = new HashMap<>();

    /**
     * Registers a route with its required role (session auth).
     *
     * @param path Route path pattern
     * @param role Required role name
     */
    public static void registerPathWithRole(String path, String role) {
        pathToRole.put(path, role);
    }

    /**
     * Registers a route as login-required (session auth).
     *
     * @param path Route path pattern
     */
    public static void registerLoginRequired(String path) {
        loginRequiredPaths.add(path);
    }

    /**
     * Registers a route as Bearer token required.
     *
     * @param path Route path pattern
     */
    public static void registerTokenRequired(String path) {
        tokenRequiredPaths.add(path);
    }

    /**
     * Registers a route with its required role (token auth).
     *
     * @param path Route path pattern
     * @param role Required role name
     */
    public static void registerTokenPathWithRole(String path, String role) {
        tokenPathToRole.put(path, role);
    }

    /**
     * Checks access for the current request.
     * Dispatches to token or session auth based on route registration.
     *
     * @param req HTTP request
     * @param res HTTP response
     */
    public static void checkAccess(Request req, Response res)
    {
        String path = req.matchedPath();

        if (tokenRequiredPaths.contains(path)) {
            Auth.requireToken(req, res);
            return;
        }

        if (tokenPathToRole.containsKey(path)) {
            Auth.requireTokenRole(req, res, tokenPathToRole.get(path));
            return;
        }

        if (loginRequiredPaths.contains(path)) {
            Auth.requireLogin(req, res);
        }

        String requiredRole = pathToRole.get(path);
        if (requiredRole == null) return;

        Auth.requireLogin(req, res);

        UserDetails user = Auth.user(req);
        String userRole = user.getRole();
        if (userRole == null || !userRole.equals(requiredRole)) {
            res.redirect(Obsidian.loadConfigAndEnv().get("SITE_URL"));
            halt();
        }
    }
}