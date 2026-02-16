package fr.kainovaii.obsidian.validation;

/**
 * Exception thrown when validation fails.
 * Contains validation errors for inspection.
 */
public class ValidationException extends RuntimeException {
    /** Validation errors */
    private final ValidationErrors errors;
    
    /**
     * Constructor with validation errors.
     * 
     * @param errors Validation errors
     */
    public ValidationException(ValidationErrors errors) {
        super("The given data was invalid");
        this.errors = errors;
    }
    
    /**
     * Gets validation errors.
     * 
     * @return ValidationErrors instance
     */
    public ValidationErrors getErrors() {
        return errors;
    }
}
