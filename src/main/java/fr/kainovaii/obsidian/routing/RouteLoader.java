package fr.kainovaii.obsidian.routing;

import fr.kainovaii.obsidian.security.role.HasRole;
import fr.kainovaii.obsidian.security.role.RoleChecker;
import fr.kainovaii.obsidian.routing.methods.*;
import fr.kainovaii.obsidian.security.user.RequireLogin;
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
 * Single-pass per method: iterates the annotation map once instead of calling
 * one check per HTTP verb.
 */
public class RouteLoader
{
    /** Logger instance */
    private static final Logger logger = LoggerFactory.getLogger(RouteLoader.class);

    /**
     * Maps each HTTP annotation type to its Spark registration function.
     * Insertion order is preserved but irrelevant — each method carries at most one HTTP annotation.
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
     * Single pass per method: checks each annotation type from the map and stops at the first match.
     *
     * @param controller Controller instance
     */
    private static void registerControllerRoutes(Object controller)
    {
        Class<?> controllerClass = controller.getClass();

        for (Method method : controllerClass.getDeclaredMethods()) {
            for (Map.Entry<Class<? extends Annotation>, BiConsumer<String, spark.Route>> entry : SPARK_REGISTRARS.entrySet()) {
                Annotation annotation = method.getAnnotation(entry.getKey());
                if (annotation == null) continue;

                String path = getAnnotationValue(annotation);
                String name = getAnnotationName(annotation);

                Route.registerNamedRoute(name, path);
                registerAccessIfPresent(controllerClass, method, path);
                entry.getValue().accept(path, RouteHandler.create(controller, method));

                logger.debug("Registered {} route: {} -> {}", entry.getKey().getSimpleName(), name, path);
                break;
            }
        }
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