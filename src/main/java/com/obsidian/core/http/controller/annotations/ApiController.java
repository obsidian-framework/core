package com.obsidian.core.http.controller.annotations;

import java.lang.annotation.*;

/**
 * Marks a controller as API-only.
 * All routes in this controller use Bearer token authentication instead of sessions.
 * Supports an optional path prefix: @ApiController("/api/v1")
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiController {
    String value() default "";
}