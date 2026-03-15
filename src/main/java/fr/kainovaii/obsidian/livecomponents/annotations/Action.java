package fr.kainovaii.obsidian.livecomponents.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a callable LiveComponent action.
 * Only methods annotated with {@code @Action} can be invoked from the client.
 * Provides an explicit, secure contract between the template and the component.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Action {
}