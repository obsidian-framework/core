package fr.kainovaii.core.security.role;

import fr.kainovaii.core.Spark;
import fr.kainovaii.core.web.controller.BaseController;
import spark.Request;
import spark.Response;

import java.util.HashMap;
import java.util.Map;

import static spark.Spark.halt;

public class RoleChecker extends BaseController
{
    private static final Map<String, String> pathToRole = new HashMap<>();

    public static void registerPathWithRole(String path, String role) {
        pathToRole.put(path, role);
    }

    public static void checkAccess(Request req, Response res)
    {
        String matchedPattern = req.matchedPath();
        String requiredRole = pathToRole.get(matchedPattern);

        if (requiredRole == null) { return; }

        requireLogin(req, res);

        if (!requiredRole.equals("DEFAULT"))
        {
            String userRole = getLoggedUser(req).getRole();
            if (userRole == null || !userRole.equals(requiredRole)) {
                redirectWithFlash(req, res, "error", "Access denied - Role required : " + requiredRole, Spark.loadConfigAndEnv().get("SITE_URL"));
                halt();
            }
        }
    }
}