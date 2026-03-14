package fr.kainovaii.obsidian.security.user;

import java.lang.annotation.*;

/**
 * Requires the user to be authenticated to access the route.
 * If the user is not logged in, they are redirected to the login page.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireLogin
{
}