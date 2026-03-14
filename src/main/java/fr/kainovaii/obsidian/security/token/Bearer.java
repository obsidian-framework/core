package fr.kainovaii.obsidian.security.token;

import java.lang.annotation.*;

/**
 * Requires Bearer token authentication for a single route.
 * Use on a method to override session-based auth on an otherwise classic controller.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Bearer
{
}