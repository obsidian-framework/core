package fr.kainovaii.obsidian.core.web.controller;

import fr.kainovaii.obsidian.core.Obsidian;
import fr.kainovaii.obsidian.core.security.csrf.CsrfProtection;
import fr.kainovaii.obsidian.core.security.user.UserDetails;
import fr.kainovaii.obsidian.core.security.user.UserDetailsService;
import fr.kainovaii.obsidian.core.security.user.UserDetailsServiceImpl;
import fr.kainovaii.obsidian.core.web.ApiResponse;
import fr.kainovaii.obsidian.core.web.di.Container;
import fr.kainovaii.obsidian.core.web.error.ErrorHandler;
import fr.kainovaii.obsidian.core.web.template.TemplateManager;
import org.mindrot.jbcrypt.BCrypt;
import spark.*;
import java.util.*;

import static spark.Spark.halt;

public class BaseController extends ApiResponse
{
    private static UserDetailsService userService;

    protected static UserDetailsService getUserService() {
        if (userService == null) {
            try {
                userService = Container.resolve(UserDetailsService.class);
            } catch (Exception e) {
                userService = autoDetectUserDetailsService();
            }
        }
        return userService;
    }

    private static UserDetailsService autoDetectUserDetailsService() {
        try {
            org.reflections.Reflections reflections = new org.reflections.Reflections(Obsidian.getBasePackage());

            java.util.Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(UserDetailsServiceImpl.class);

            if (annotatedClasses.isEmpty()) {
                throw new RuntimeException("No class annotated with @UserDetailsServiceImpl found in " + Obsidian.getBasePackage());
            }

            Class<?> implClass = annotatedClasses.iterator().next();

            if (!UserDetailsService.class.isAssignableFrom(implClass)) {
                throw new RuntimeException(implClass.getName() + " is annotated with @UserDetailsServiceImpl but doesn't implement UserDetailsService");
            }

            try {
                return (UserDetailsService) implClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                java.lang.reflect.Constructor<?>[] constructors = implClass.getConstructors();
                if (constructors.length > 0) {
                    java.lang.reflect.Constructor<?> constructor = constructors[0];
                    java.lang.reflect.Parameter[] params = constructor.getParameters();
                    Object[] args = new Object[params.length];

                    for (int i = 0; i < params.length; i++) {
                        args[i] = Container.resolve(params[i].getType());
                    }
                    return (UserDetailsService) constructor.newInstance(args);
                }
                throw e;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to auto-detect UserDetailsService: " + e.getMessage(), e);
        }
    }

    protected static boolean login(String username, String password, Session session)
    {
        UserDetails user = getUserService().loadByUsername(username);
        if (user == null || !user.isEnabled()) return false;

        if (BCrypt.checkpw(password, user.getPassword()))
        {
            session.attribute("logged", true);
            session.attribute("user_id", user.getId());
            session.attribute("username", user.getUsername());
            session.attribute("role", user.getRole());
            return true;
        }
        return false;
    }

    protected static void logout(Session session) {
        if (session != null) session.invalidate();
    }

    public static boolean isLogged(Request req)
    {
        Session session = req.session(false);
        if (session == null) return false;

        Boolean logged = session.attribute("logged");
        return Boolean.TRUE.equals(logged);
    }

    @SuppressWarnings("unchecked")
    public static <T extends UserDetails> T getLoggedUser(Request req)
    {
        Session session = req.session(false);
        if (session == null) return null;

        Object userId = session.attribute("user_id");
        if (userId == null) return null;

        return (T) getUserService().loadById(userId);
    }

    protected static boolean hasRole(Request req, String role)
    {
        UserDetails user = getLoggedUser(req);
        return user != null && role.equals(user.getRole());
    }

    protected static void requireLogin(Request req, Response res)
    {
        if (!isLogged(req)) {
            res.redirect("/users/login");
            halt();
        }
    }

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

    public static Map<String, String> collectFlashes(Request req)
    {
        Session session = req.session(false);
        if (session == null) return new HashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, String> flashes = (Map<String, String>) session.attribute("_flash_messages");

        if (flashes == null) { return new HashMap<>(); }
        session.removeAttribute("_flash_messages");
        return flashes;
    }

    protected static Object redirectWithFlash(Request req, Response res, String type, String message, String location)
    {
        setFlash(req, type, message);
        res.redirect(location);
        halt();
        return null;
    }

    protected String csrfToken(Request req) {
        return CsrfProtection.getToken(req);
    }

    protected boolean validateCsrf(Request req) {
        return CsrfProtection.validate(req);
    }

    protected void regenerateCsrfToken(Request req) {
        CsrfProtection.regenerateToken(req);
    }

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