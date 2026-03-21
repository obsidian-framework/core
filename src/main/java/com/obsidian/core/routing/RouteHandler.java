package com.obsidian.core.routing;

import com.obsidian.core.security.auth.Auth;
import com.obsidian.core.security.SessionKeys;
import com.obsidian.core.security.csrf.annotations.CsrfProtect;
import com.obsidian.core.security.csrf.CsrfProtection;
import com.obsidian.core.security.role.RoleChecker;
import com.obsidian.core.security.user.CurrentUser;
import com.obsidian.core.di.Container;
import com.obsidian.core.error.ErrorHandler;
import com.obsidian.core.http.middleware.Middleware;
import com.obsidian.core.http.middleware.annotations.After;
import com.obsidian.core.http.middleware.annotations.Before;
import com.obsidian.core.http.middleware.MiddlewareManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * Creates Spark route handlers with middleware, CSRF protection, and error handling.
 * Handles method parameter injection and exception handling.
 */
public class RouteHandler
{
    /** Logger instance */
    private static final Logger logger = LoggerFactory.getLogger(RouteHandler.class);

    /** Empty middleware array used when no @Before or @After annotation is present */
    @SuppressWarnings("unchecked")
    private static final Class<? extends Middleware>[] EMPTY_MIDDLEWARES = new Class[0];

    /**
     * Creates Spark route handler for controller method.
     * Wraps method with middleware, CSRF validation, and error handling.
     *
     * @param controller Controller instance
     * @param method Controller method
     * @return Spark route handler
     */
    public static spark.Route create(Object controller, Method method)
    {
        return (req, res) ->
        {
            Object result = null;
            try {
                RoleChecker.checkAccess(req, res);
                executeBeforeMiddleware(method, req, res);
                validateCsrf(controller, method, req, res);
                result = invokeMethod(controller, method, req, res);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                result = ErrorHandler.handle(cause, req, res);
            } catch (Exception e) {
                result = ErrorHandler.handle(e, req, res);
            } catch (Throwable t) {
                result = ErrorHandler.handle(t, req, res);
            } finally {
                executeAfterMiddleware(method, req, res);
            }
            return result;
        };
    }

    /**
     * Executes before middleware chain.
     * Built-in middlewares always run. User-defined middlewares run if {@link Before} is present.
     *
     * @param method Controller method
     * @param req HTTP request
     * @param res HTTP response
     * @throws Exception if any middleware throws an exception
     */
    private static void executeBeforeMiddleware(Method method, Request req, Response res) throws Exception
    {
        Class<? extends Middleware>[] userMiddlewares = method.isAnnotationPresent(Before.class)
                ? method.getAnnotation(Before.class).value()
                : EMPTY_MIDDLEWARES;

        MiddlewareManager.executeBefore(userMiddlewares, req, res);
    }

    /**
     * Validates CSRF token if {@link CsrfProtect} annotation present.
     *
     * @param controller Controller instance
     * @param method Controller method
     * @param req HTTP request
     * @param res HTTP response
     * @throws SecurityException if CSRF validation fails
     */
    private static void validateCsrf(Object controller, Method method, Request req, Response res)
    {
        if (method.isAnnotationPresent(CsrfProtect.class))
        {
            if (!CsrfProtection.validate(req)) {
                logger.warn("CSRF validation failed for {}.{}",
                        controller.getClass().getSimpleName(),
                        method.getName());

                if (req.session(false) != null) {
                    req.session().attribute(SessionKeys.FLASH_ERROR, "Invalid security token. Please try again.");
                }

                res.status(403);
                throw new SecurityException("CSRF token validation failed");
            }
        }
    }

    /**
     * Invokes controller method with resolved parameters.
     *
     * @param controller Controller instance
     * @param method Controller method
     * @param req HTTP request
     * @param res HTTP response
     * @return Method return value
     * @throws Exception if invocation fails
     */
    private static Object invokeMethod(Object controller, Method method, Request req, Response res) throws Exception
    {
        method.setAccessible(true);
        Object[] args = resolveMethodParameters(method, req, res);
        return method.invoke(controller, args);
    }

    /**
     * Resolves method parameters via dependency injection.
     * Injects Request, Response, {@link CurrentUser}, or resolves from Container.
     *
     * @param method Controller method
     * @param req HTTP request
     * @param res HTTP response
     * @return Array of resolved parameters
     */
    private static Object[] resolveMethodParameters(Method method, Request req, Response res)
    {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Class<?> paramType = parameters[i].getType();

            if (paramType == Request.class) {
                args[i] = req;
            } else if (paramType == Response.class) {
                args[i] = res;
            } else if (parameters[i].isAnnotationPresent(CurrentUser.class)) {
                args[i] = Auth.user();
            } else {
                args[i] = Container.resolve(paramType);
            }
        }

        return args;
    }

    /**
     * Executes after middleware chain.
     * Built-in middlewares always run. User-defined middlewares run if {@link After} is present.
     *
     * @param method Controller method
     * @param req HTTP request
     * @param res HTTP response
     * @throws Exception if any middleware throws an exception
     */
    private static void executeAfterMiddleware(Method method, Request req, Response res) throws Exception
    {
        Class<? extends Middleware>[] userMiddlewares = method.isAnnotationPresent(After.class)
                ? method.getAnnotation(After.class).value()
                : EMPTY_MIDDLEWARES;

        MiddlewareManager.executeAfter(userMiddlewares, req, res);
    }
}