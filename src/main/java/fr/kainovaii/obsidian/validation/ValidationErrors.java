package fr.kainovaii.obsidian.validation;

import java.util.HashMap;
import java.util.Map;

/**
 * Validation errors container.
 * Stores field-level validation errors.
 */
public class ValidationErrors {
    /** Error messages mapped by field name */
    private final Map<String, String> errors = new HashMap<>();

    /**
     * Adds error for field.
     * Only stores the first error per field.
     *
     * @param field Field name
     * @param message Error message
     */
    public void add(String field, String message) {
        if (!errors.containsKey(field)) {
            errors.put(field, message);
        }
    }

    /**
     * Checks if validation has any errors.
     *
     * @return true if errors exist
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Checks if specific field has error.
     *
     * @param field Field name
     * @return true if field has error
     */
    public boolean has(String field) {
        return errors.containsKey(field);
    }

    /**
     * Gets error for specific field.
     *
     * @param field Field name
     * @return Error message or null
     */
    public String get(String field) {
        return errors.get(field);
    }

    /**
     * Gets all errors.
     *
     * @return Map of field to error message
     */
    public Map<String, String> all() {
        return new HashMap<>(errors);
    }

    /**
     * Gets errors map for JSON serialization.
     * Used by Jackson to serialize ValidationErrors to JSON.
     *
     * @return Errors map
     */
    public Map<String, String> getErrors() {
        return errors;
    }

    /**
     * Gets first error message.
     *
     * @return First error or null
     */
    public String first() {
        return errors.values().stream().findFirst().orElse(null);
    }

    /**
     * Counts total errors.
     *
     * @return Number of errors
     */
    public int count() {
        return errors.size();
    }
}