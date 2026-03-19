package com.obsidian.core.http.controller;

import com.obsidian.core.error.ErrorHandler;
import com.obsidian.core.http.RequestContext;
import com.obsidian.core.http.ResponseContext;
import com.obsidian.core.security.auth.Auth;
import com.obsidian.core.security.csrf.CsrfProtection;
import com.obsidian.core.security.user.UserDetails;
import com.obsidian.core.template.TemplateManager;
import spark.Request;
import spark.Response;
import spark.Session;


import java.util.HashMap;
import java.util.Map;

import static spark.Spark.halt;

/**
* Base controller providing template rendering, flash messages, and CSRF helpers.
* Authentication methods delegate to {@link Auth} and are kept here for convenience.
*/
public class BaseController
{
    /**
     * Authenticates user and creates session.
     *
     * @param username Username
     * @param password Plain text password
     * @param request HTTP request
     * @return true if login successful, false otherwise
     */
    protected static boolean login(String username, String password, Request request) {
        return Auth.login(username, password, request);
    }

    /**
     * Logs out user by invalidating session.
     *
     * @param session HTTP session
     */
    protected static void logout(Session session) {
        Auth.logout(session);
    }

    /**
     * Checks if user is logged in.
     *
     * @param req HTTP request
     * @return true if authenticated, false otherwise
     */
    public static boolean isLogged(Request req) {
        return Auth.isLogged(req);
    }

    /**
     * Gets currently logged in user.
     * Result is cached per request — no redundant DB calls.
     *
     * @param req HTTP request
     * @param <T> UserDetails type
     * @return User details or null if not logged in
     */
    public static <T extends UserDetails> T getLoggedUser(Request req) {
        return Auth.user(req);
    }

    /**
     * Checks if logged in user has specific role.
     *
     * @param req HTTP request
     * @param role Role name
     * @return true if user has role, false otherwise
     */
    protected static boolean hasRole(Request req, String role) {
        return Auth.hasRole(req, role);
    }

    /**
     * Requires user to be logged in or redirects to login page.
     *
     * @param req HTTP request
     * @param res HTTP response
     */
    protected static void requireLogin(Request req, Response res) {
        Auth.requireLogin(req, res);
    }

    /**
     * Hashes a plain text password using BCrypt.
     *
     * @param password Plain text password
     * @return BCrypt hashed password
     */
    protected static String hashPassword(String password) {
        return Auth.hashPassword(password);
    }

    /**
     * Verifies a plain text password against a BCrypt hash.
     *
     * @param password Plain text password
     * @param hash BCrypt hash
     * @return true if password matches hash, false otherwise
     */
    protected static boolean checkPassword(String password, String hash) {
        return Auth.checkPassword(password, hash);
    }

    /**
     * Sets a flash message for next request.
     *
     * @param req HTTP request
     * @param type Message type (success, error, info, warning)
     * @param message Message text
     */
    protected static void setFlash(Request req, String type, String message)
    {
        Session session = req.session();
        @SuppressWarnings("unchecked")
        Map<String, String> flashes = (Map<String, String>) session.attribute("_flash_messages");

        if (flashes == null) {
            flashes = new HashMap<>();
            session.attribute("_flash_messages", flashes);
        }

        flashes.put(type, message);
    }

    /**
     * Collects and removes flash messages from session.
     *
     * @param req HTTP request
     * @return Map of flash messages
     */
    public static Map<String, String> collectFlashes(Request req)
    {
        Session session = req.session(false);
        if (session == null) return new HashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, String> flashes = (Map<String, String>) session.attribute("_flash_messages");

        if (flashes == null) return new HashMap<>();
        session.removeAttribute("_flash_messages");
        return flashes;
    }

    /**
     * Sets flash message and redirects.
     *
     * @param type Message type
     * @param message Message text
     * @param location Redirect location
     * @return null (never reached due to halt)
     */
    protected static Object redirectWithFlash(String type, String message, String location) {
        setFlash(RequestContext.get(), type, message);
        ResponseContext.get().redirect(location);
        halt();
        return null;
    }

    /**
     * Sets a success flash message and redirects.
     *
     * @param message Success message text
     * @param location Redirect location
     * @return null (never reached due to halt)
     */
    protected static Object redirectWithSuccess(String message, String location) {
        return redirectWithFlash("success", message, location);
    }

    /**
     * Sets an error flash message and redirects.
     *
     * @param message Error message text
     * @param location Redirect location
     * @return null (never reached due to halt)
     */
    protected static Object redirectWithError(String message, String location) {
        return redirectWithFlash("error", message, location);
    }

    /**
     * Sets a warning flash message and redirects.
     *
     * @param message Warning message text
     * @param location Redirect location
     * @return null (never reached due to halt)
     */
    protected static Object redirectWithWarning(String message, String location) {
        return redirectWithFlash("warning", message, location);
    }

    /**
     * Sets an info flash message and redirects.
     *
     * @param message Info message text
     * @param location Redirect location
     * @return null (never reached due to halt)
     */
    protected static Object redirectWithInfo(String message, String location) {
        return redirectWithFlash("info", message, location);
    }

    /**
     * Gets CSRF token for current request.
     *
     * @param req HTTP request
     * @return CSRF token
     */
    protected String csrfToken(Request req) {
        return CsrfProtection.getToken(req);
    }

    /**
     * Validates CSRF token from request.
     *
     * @param req HTTP request
     * @return true if valid, false otherwise
     */
    protected boolean validateCsrf(Request req) {
        return CsrfProtection.validate(req);
    }

    /**
     * Regenerates CSRF token.
     *
     * @param req HTTP request
     */
    protected void regenerateCsrfToken(Request req) {
        CsrfProtection.regenerateToken(req);
    }

    /**
     * Renders template with model data.
     *
     * @param template Template path (relative to view/)
     * @param model Template variables
     * @return Rendered HTML
     */
    protected String render(String template, Map<String, Object> model)
    {
        Map<String, Object> merged = new HashMap<>(TemplateManager.getGlobals());
        if (model != null) merged.putAll(model);

        try {
            return TemplateManager.get().render("view/" + template, merged);
        } catch (Exception exception) {
            Request req = (Request) merged.get("request");
            Response res = (Response) merged.get("response");
            return ErrorHandler.handle(exception, req, res);
        }
    }

}