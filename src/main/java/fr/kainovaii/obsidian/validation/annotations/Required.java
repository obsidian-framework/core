package fr.kainovaii.obsidian.validation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as required.
 * Field cannot be null or empty.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Required {
    /**
     * Custom error message.
     * 
     * @return Error message
     */
    String message() default "This field is required";
}
