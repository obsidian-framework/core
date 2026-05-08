package com.obsidian.core.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method inside a {@link Listener}-annotated class as a handler for
 * events of the specified type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface On
{
    /** The event type this method handles. */
    Class<?> value();

    /**
     * Execution priority. Higher values run first. Default 0.
     *
     * <p>Conventional range: -100 to +100. Use values outside that range
     * sparingly — they signal "this listener must run before/after every
     * normal listener", which is rarely the right design.
     */
    int priority() default 0;
}
