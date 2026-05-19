package com.obsidian.core.event.annotations;

import com.obsidian.core.event.Event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an event handler for the given event type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface On
{
    Class<? extends Event> value();
    int priority() default 0;
}