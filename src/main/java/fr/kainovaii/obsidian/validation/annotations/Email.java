package fr.kainovaii.obsidian.validation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates email format.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Email {
    /**
     * Custom error message.
     * 
     * @return Error message
     */
    String message() default "Invalid email format";
}
