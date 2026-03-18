package com.obsidian.core.security.role;

import java.lang.annotation.*;

/**
 * Restricts route access to users with a specific role.
 * Authentication is always enforced when this annotation is present.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface HasRole
{
    String value();
}