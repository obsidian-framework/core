package com.obsidian.core.security.user;

import java.lang.annotation.*;

/**
 * Injects the currently authenticated user into a controller method parameter.
 * The parameter must implement {@link UserDetails}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser
{
}