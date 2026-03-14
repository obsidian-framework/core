package fr.kainovaii.obsidian.routing;

import fr.kainovaii.obsidian.security.role.HasRole;
import fr.kainovaii.obsidian.security.role.RoleChecker;
import fr.kainovaii.obsidian.routing.methods.*;
import fr.kainovaii.obsidian.security.user.RequireLogin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;

import static spark.Spark.*;

/**
 * Route registration system.
 * Discovers HTTP method annotations and registers routes with Spark.
 */
public class RouteLoader
{
    /** Logger instance */
    private static final Logger logger = LoggerFactory.getLogger(RouteLoader.class);

    /**
     * Registers routes from all controllers.
     *
     * @param controllers List of controller instances
     */
    public static void registerRoutes(List<Object> controllers)
    {
        for (Object controller : controllers) {
            registerControllerRoutes(controller);
        }
    }

    /**
     * Registers routes from a single controller.
     * Scans for HTTP method annotations on all methods.
     *
     * @param controller Controller instance
     */
    private static void registerControllerRoutes(Object controller)
    {
        for (Method method : controller.getClass().getDeclaredMethods()) {
            registerGetRoute(controller, method);
            registerPostRoute(controller, method);
            registerPutRoute(controller, method);
            registerPatchRoute(controller, method);
            registerDeleteRoute(controller, method);
            registerOptionsRoute(controller, method);
            registerHeadRoute(controller, method);
        }
    }

    /**
     * Registers GET route if method has @GET annotation.
     *
     * @param controller Controller instance
     * @param method Controller method
     */
    private static void registerGetRoute(Object controller, Method method)
    {
        if (!method.isAnnotationPresent(GET.class)) return;

        GET annotation = method.getAnnotation(GET.class);
        String path = annotation.value();
        String name = annotation.name();

        Route.registerNamedRoute(name, path);
        registerAccessIfPresent(controller.getClass(), method, path);
        get(path, RouteHandler.create(controller, method));

        logger.debug("Registered GET route: {} -> {}", name, path);
    }

    /**
     * Registers POST route if method has @POST annotation.
     *
     * @param controller Controller instance
     * @param method Controller method
     */
    private static void registerPostRoute(Object controller, Method method)
    {
        if (!method.isAnnotationPresent(POST.class)) return;

        POST annotation = method.getAnnotation(POST.class);
        String path = annotation.value();
        String name = annotation.name();

        Route.registerNamedRoute(name, path);
        registerAccessIfPresent(controller.getClass(), method, path);
        post(path, RouteHandler.create(controller, method));

        logger.debug("Registered POST route: {} -> {}", name, path);
    }

    /**
     * Registers PUT route if method has @PUT annotation.
     *
     * @param controller Controller instance
     * @param method Controller method
     */
    private static void registerPutRoute(Object controller, Method method)
    {
        if (!method.isAnnotationPresent(PUT.class)) return;

        PUT annotation = method.getAnnotation(PUT.class);
        String path = annotation.value();
        String name = annotation.name();

        Route.registerNamedRoute(name, path);
        registerAccessIfPresent(controller.getClass(), method, path);
        put(path, RouteHandler.create(controller, method));

        logger.debug("Registered PUT route: {} -> {}", name, path);
    }

    /**
     * Registers PATCH route if method has @PATCH annotation.
     *
     * @param controller Controller instance
     * @param method Controller method
     */
    private static void registerPatchRoute(Object controller, Method method)
    {
        if (!method.isAnnotationPresent(PATCH.class)) return;

        PATCH annotation = method.getAnnotation(PATCH.class);
        String path = annotation.value();
        String name = annotation.name();

        Route.registerNamedRoute(name, path);
        registerAccessIfPresent(controller.getClass(), method, path);
        patch(path, RouteHandler.create(controller, method));

        logger.debug("Registered PATCH route: {} -> {}", name, path);
    }

    /**
     * Registers DELETE route if method has @DELETE annotation.
     *
     * @param controller Controller instance
     * @param method Controller method
     */
    private static void registerDeleteRoute(Object controller, Method method)
    {
        if (!method.isAnnotationPresent(DELETE.class)) return;

        DELETE annotation = method.getAnnotation(DELETE.class);
        String path = annotation.value();
        String name = annotation.name();

        Route.registerNamedRoute(name, path);
        registerAccessIfPresent(controller.getClass(), method, path);
        delete(path, RouteHandler.create(controller, method));

        logger.debug("Registered DELETE route: {} -> {}", name, path);
    }

    /**
     * Registers OPTIONS route if method has @OPTIONS annotation.
     *
     * @param controller Controller instance
     * @param method Controller method
     */
    private static void registerOptionsRoute(Object controller, Method method)
    {
        if (!method.isAnnotationPresent(OPTIONS.class)) return;

        OPTIONS annotation = method.getAnnotation(OPTIONS.class);
        String path = annotation.value();
        String name = annotation.name();

        Route.registerNamedRoute(name, path);
        registerAccessIfPresent(controller.getClass(), method, path);
        options(path, RouteHandler.create(controller, method));

        logger.debug("Registered OPTIONS route: {} -> {}", name, path);
    }

    /**
     * Registers HEAD route if method has @HEAD annotation.
     *
     * @param controller Controller instance
     * @param method Controller method
     */
    private static void registerHeadRoute(Object controller, Method method)
    {
        if (!method.isAnnotationPresent(HEAD.class)) return;

        HEAD annotation = method.getAnnotation(HEAD.class);
        String path = annotation.value();
        String name = annotation.name();

        Route.registerNamedRoute(name, path);
        registerAccessIfPresent(controller.getClass(), method, path);
        head(path, RouteHandler.create(controller, method));

        logger.debug("Registered HEAD route: {} -> {}", name, path);
    }

    /**
     * Registers access control for a route path.
     *
     * @param controllerClass Controller class
     * @param method Controller method
     * @param path Route path
     */
    private static void registerAccessIfPresent(Class<?> controllerClass, Method method, String path)
    {
        // Method-level Bearer
        if (method.isAnnotationPresent(fr.kainovaii.obsidian.security.token.Bearer.class)) {
            RoleChecker.registerTokenRequired(path);
            return;
        }

        // Method-level HasRole
        if (method.isAnnotationPresent(HasRole.class)) {
            RoleChecker.registerPathWithRole(path, method.getAnnotation(HasRole.class).value());
            return;
        }

        // Method-level RequireLogin
        if (method.isAnnotationPresent(RequireLogin.class)) {
            RoleChecker.registerLoginRequired(path);
            return;
        }

        // Class-level ApiController
        if (controllerClass.isAnnotationPresent(fr.kainovaii.obsidian.http.controller.annotations.ApiController.class)) {
            if (method.isAnnotationPresent(HasRole.class)) {
                RoleChecker.registerTokenPathWithRole(path, method.getAnnotation(HasRole.class).value());
            } else {
                RoleChecker.registerTokenRequired(path);
            }
            return;
        }

        // Class-level HasRole
        if (controllerClass.isAnnotationPresent(HasRole.class)) {
            RoleChecker.registerPathWithRole(path, controllerClass.getAnnotation(HasRole.class).value());
            return;
        }

        // Class-level RequireLogin
        if (controllerClass.isAnnotationPresent(RequireLogin.class)) {
            RoleChecker.registerLoginRequired(path);
        }
    }
}