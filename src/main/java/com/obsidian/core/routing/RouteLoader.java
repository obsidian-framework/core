package com.obsidian.core.routing;

import com.obsidian.core.http.controller.annotations.ApiController;
import com.obsidian.core.http.controller.annotations.Controller;
import com.obsidian.core.routing.methods.*;
import com.obsidian.core.security.token.Bearer;
import com.obsidian.core.security.role.HasRole;
import com.obsidian.core.security.role.RoleChecker;
import com.obsidian.core.security.user.RequireLogin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static spark.Spark.*;

/**
 * Route registration system.
 * Discovers HTTP method annotations and registers routes with Spark.
 * Supports controller-level path prefix via @Controller("/prefix").
 */
public class RouteLoader
{
    /** Logger instance */
    private static final Logger logger = LoggerFactory.getLogger(RouteLoader.class);

    /**
     * Maps each HTTP annotation type to its Spark registration function.
     */
    private static final Map<Class<? extends Annotation>, BiConsumer<String, spark.Route>> SPARK_REGISTRARS = Map.of(
            GET.class,     (path, handler) -> get(path, handler),
            POST.class,    (path, handler) -> post(path, handler),
            PUT.class,     (path, handler) -> put(path, handler),
            PATCH.class,   (path, handler) -> patch(path, handler),
            DELETE.class,  (path, handler) -> delete(path, handler),
            OPTIONS.class, (path, handler) -> options(path, handler),
            HEAD.class,    (path, handler) -> head(path, handler)
    );

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
     * Applies controller-level prefix from @Controller("value") to all routes.
     *
     * @param controller Controller instance
     */
    private static void registerControllerRoutes(Object controller)
    {
        Class<?> controllerClass = controller.getClass();
        String prefix = resolvePrefix(controllerClass);

        for (Method method : controllerClass.getDeclaredMethods()) {
            for (Map.Entry<Class<? extends Annotation>, BiConsumer<String, spark.Route>> entry : SPARK_REGISTRARS.entrySet()) {
                Annotation annotation = method.getAnnotation(entry.getKey());
                if (annotation == null) continue;

                String path   = prefix + getAnnotationValue(annotation);
                String name   = getAnnotationName(annotation);

                Route.registerNamedRoute(name, path);
                registerAccessIfPresent(controllerClass, method, path);
                entry.getValue().accept(path, RouteHandler.create(controller, method));

                logger.debug("Registered {} route: {} -> {}", entry.getKey().getSimpleName(), name, path);
                break;
            }
        }
    }

    /**
     * Resolves the controller-level path prefix.
     * Reads value() from @Controller or @ApiController (whichever is present).
     * Returns "" if neither carries a value.
     *
     * @param controllerClass Controller class
     * @return Prefix string, never null
     */
    private static String resolvePrefix(Class<?> controllerClass)
    {
        Controller controllerAnn = controllerClass.getAnnotation(Controller.class);
        ApiController apiAnn     = controllerClass.getAnnotation(ApiController.class);

        String raw = "";
        if (controllerAnn != null && !controllerAnn.value().isEmpty()) {
            raw = controllerAnn.value();
        } else if (apiAnn != null && !apiAnn.value().isEmpty()) {
            raw = apiAnn.value();
        }

        String prefix = raw.trim();

        // Ensure it starts with "/" if non-empty, and strip trailing "/"
        if (!prefix.isEmpty()) {
            if (!prefix.startsWith("/")) prefix = "/" + prefix;
            prefix = prefix.replaceAll("/+$", "");
        }

        return prefix;
    }

    /**
     * Extracts the {@code value()} attribute from an HTTP annotation via reflection.
     *
     * @param annotation HTTP annotation instance
     * @return Route path
     */
    private static String getAnnotationValue(Annotation annotation)
    {
        try {
            return (String) annotation.annotationType().getMethod("value").invoke(annotation);
        } catch (Exception e) {
            throw new RuntimeException("Cannot read 'value' from annotation " + annotation.annotationType().getSimpleName(), e);
        }
    }

    /**
     * Extracts the {@code name()} attribute from an HTTP annotation via reflection.
     *
     * @param annotation HTTP annotation instance
     * @return Route name, or empty string if absent
     */
    private static String getAnnotationName(Annotation annotation)
    {
        try {
            return (String) annotation.annotationType().getMethod("name").invoke(annotation);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Registers access control for a route path.
     *
     * @param controllerClass Controller class
     * @param method Controller method
     * @param path Route path (already prefixed)
     */
    private static void registerAccessIfPresent(Class<?> controllerClass, Method method, String path)
    {
        if (method.isAnnotationPresent(Bearer.class)) {
            RoleChecker.registerTokenRequired(path);
            return;
        }

        if (method.isAnnotationPresent(HasRole.class)) {
            RoleChecker.registerPathWithRole(path, method.getAnnotation(HasRole.class).value());
            return;
        }

        if (method.isAnnotationPresent(RequireLogin.class)) {
            RoleChecker.registerLoginRequired(path);
            return;
        }

        if (controllerClass.isAnnotationPresent(ApiController.class)) {
            if (method.isAnnotationPresent(HasRole.class)) {
                RoleChecker.registerTokenPathWithRole(path, method.getAnnotation(HasRole.class).value());
            } else {
                RoleChecker.registerTokenRequired(path);
            }
            return;
        }

        if (controllerClass.isAnnotationPresent(HasRole.class)) {
            RoleChecker.registerPathWithRole(path, controllerClass.getAnnotation(HasRole.class).value());
            return;
        }

        if (controllerClass.isAnnotationPresent(RequireLogin.class)) {
            RoleChecker.registerLoginRequired(path);
        }
    }
}