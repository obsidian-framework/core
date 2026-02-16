package fr.kainovaii.obsidian.validation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates maximum string length.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Max {
    /**
     * Maximum length value.
     * 
     * @return Maximum length
     */
    int value();
    
    /**
     * Custom error message.
     * Use {value} placeholder for the maximum value.
     * 
     * @return Error message
     */
    String message() default "Value must not exceed {value} characters";
}
