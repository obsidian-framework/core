package fr.kainovaii.obsidian.validation;

import java.util.Map;

/**
 * Validation result container.
 * Contains validated data and any errors.
 */
public class ValidationResult {
    /** Validated data */
    private final Map<String, Object> data;
    
    /** Validation errors */
    private final ValidationErrors errors;
    
    /**
     * Constructor.
     * 
     * @param data Validated data map
     * @param errors Validation errors
     */
    public ValidationResult(Map<String, Object> data, ValidationErrors errors) {
        this.data = data;
        this.errors = errors;
    }
    
    /**
     * Checks if validation passed.
     * 
     * @return true if no errors
     */
    public boolean isValid() {
        return !errors.hasErrors();
    }
    
    /**
     * Checks if validation failed.
     * 
     * @return true if errors exist
     */
    public boolean fails() {
        return errors.hasErrors();
    }
    
    /**
     * Gets validated data.
     * 
     * @return Data map
     */
    public Map<String, Object> getData() {
        return data;
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
