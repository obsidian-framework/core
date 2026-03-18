package com.obsidian.core.livecomponents.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link State}-annotated field as server-only.
 *
 * Fields annotated with {@code @ServerOnly} are included in renders
 * but are never hydrated from client-supplied state, preventing
 * clients from forging sensitive values (userId, isAdmin, price, etc.).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ServerOnly {}