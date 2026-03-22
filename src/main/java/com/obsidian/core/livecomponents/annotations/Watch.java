package com.obsidian.core.livecomponents.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a state watcher, called automatically after an action
 * when the watched {@link State} field changes value.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Watch {

    /**
     * Name of the {@link State}-annotated field to observe.
     *
     * @return the field name whose value changes trigger this watcher
     */
    String value();
}