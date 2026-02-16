package fr.kainovaii.obsidian.validation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates minimum string length.
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Min {
    /**
     * Minimum length value.
     * 
     * @return Minimum length
     */
    int value();
    
    /**
     * Custom error message.
     * Use {value} placeholder for the minimum value.
     * 
     * @return Error message
     */
    String message() default "Value must be at least {value} characters";
}
