package fr.kainovaii.obsidian.validation;

import spark.Request;

import java.util.HashMap;
import java.util.Map;

/**
 * Fluent request validator with Laravel-style syntax.
 * Validates HTTP request parameters against rules.
 */
public class RequestValidator {
    
    /** HTTP request */
    private final Request request;
    
    /** Validation rules */
    private final Map<String, String> rules;
    
    /** Validation errors */
    private final ValidationErrors errors;
    
    /** Validated data */
    private final Map<String, Object> validated;
    
    /**
     * Private constructor.
     * 
     * @param request HTTP request
     * @param rules Validation rules
     */
    private RequestValidator(Request request, Map<String, String> rules)
    {
        this.request = request;
        this.rules = rules;
        this.errors = new ValidationErrors();
        this.validated = new HashMap<>();
    }
    
    /**
     * Validates request with rules.
     * Throws ValidationException if validation fails.
     * 
     * @param request HTTP request
     * @param rules Validation rules map (field -> rules string)
     * @return Validated data map
     * @throws ValidationException if validation fails
     */
    public static Map<String, Object> validate(Request request, Map<String, String> rules)
    {
        RequestValidator validator = new RequestValidator(request, rules);
        return validator.validate();
    }
    
    /**
     * Validates request and returns result without throwing.
     * 
     * @param request HTTP request
     * @param rules Validation rules map
     * @return ValidationResult with data and errors
     */
    public static ValidationResult validateSafe(Request request, Map<String, String> rules)
    {
        RequestValidator validator = new RequestValidator(request, rules);
        validator.performValidation();
        return new ValidationResult(validator.validated, validator.errors);
    }
    
    /**
     * Performs validation and throws on failure.
     * 
     * @return Validated data
     * @throws ValidationException if validation fails
     */
    private Map<String, Object> validate()
    {
        performValidation();
        
        if (errors.hasErrors()) {
            throw new ValidationException(errors);
        }
        
        return validated;
    }
    
    /**
     * Performs validation against all rules.
     */
    private void performValidation()
    {
        for (Map.Entry<String, String> entry : rules.entrySet())
        {
            String field = entry.getKey();
            String rulesString = entry.getValue();
            String value = request.queryParams(field);
            
            String[] rulesList = rulesString.split("\\|");
            
            for (String rule : rulesList) {
                if (!validateRule(field, value, rule.trim())) {
                    break;
                }
            }
            
            if (!errors.has(field) && value != null) {
                validated.put(field, value);
            }
        }
    }
    
    /**
     * Validates a single rule.
     * 
     * @param field Field name
     * @param value Field value
     * @param rule Rule string
     * @return true if validation passes
     */
    private boolean validateRule(String field, String value, String rule)
    {
        String[] parts = rule.split(":", 2);
        String ruleName = parts[0];
        String ruleParam = parts.length > 1 ? parts[1] : null;
        
        return switch (ruleName) {
            case "required" -> validateRequired(field, value);
            case "email" -> validateEmail(field, value);
            case "min" -> validateMin(field, value, Integer.parseInt(ruleParam));
            case "max" -> validateMax(field, value, Integer.parseInt(ruleParam));
            case "between" -> validateBetween(field, value, ruleParam);
            case "numeric" -> validateNumeric(field, value);
            case "integer" -> validateInteger(field, value);
            case "alpha" -> validateAlpha(field, value);
            case "alphanumeric" -> validateAlphanumeric(field, value);
            case "url" -> validateUrl(field, value);
            case "confirmed" -> validateConfirmed(field, value);
            case "unique" -> validateUnique(field, value, ruleParam);
            case "in" -> validateIn(field, value, ruleParam);
            case "regex" -> validateRegex(field, value, ruleParam);
            default -> throw new IllegalArgumentException("Unknown validation rule: " + ruleName);
        };
    }
    
    private boolean validateRequired(String field, String value)
    {
        if (value == null || value.trim().isEmpty()) {
            errors.add(field, "The " + field + " field is required");
            return false;
        }
        return true;
    }
    
    private boolean validateEmail(String field, String value)
    {
        if (value == null) return true;
        
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        if (!value.matches(emailRegex)) {
            errors.add(field, "The " + field + " must be a valid email address");
            return false;
        }
        return true;
    }
    
    private boolean validateMin(String field, String value, int min)
    {
        if (value == null) return true;
        
        if (value.length() < min) {
            errors.add(field, "The " + field + " must be at least " + min + " characters");
            return false;
        }
        return true;
    }
    
    private boolean validateMax(String field, String value, int max)
    {
        if (value == null) return true;
        
        if (value.length() > max) {
            errors.add(field, "The " + field + " may not be greater than " + max + " characters");
            return false;
        }
        return true;
    }
    
    private boolean validateBetween(String field, String value, String params)
    {
        if (value == null) return true;
        
        String[] parts = params.split(",");
        int min = Integer.parseInt(parts[0]);
        int max = Integer.parseInt(parts[1]);
        
        try {
            int numValue = Integer.parseInt(value);
            if (numValue < min || numValue > max) {
                errors.add(field, "The " + field + " must be between " + min + " and " + max);
                return false;
            }
        } catch (NumberFormatException e) {
            int length = value.length();
            if (length < min || length > max) {
                errors.add(field, "The " + field + " must be between " + min + " and " + max + " characters");
                return false;
            }
        }
        return true;
    }
    
    private boolean validateNumeric(String field, String value)
    {
        if (value == null) return true;
        
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            errors.add(field, "The " + field + " must be a number");
            return false;
        }
    }
    
    private boolean validateInteger(String field, String value)
    {
        if (value == null) return true;
        
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            errors.add(field, "The " + field + " must be an integer");
            return false;
        }
    }
    
    private boolean validateAlpha(String field, String value)
    {
        if (value == null) return true;
        
        if (!value.matches("^[a-zA-Z]+$")) {
            errors.add(field, "The " + field + " may only contain letters");
            return false;
        }
        return true;
    }
    
    private boolean validateAlphanumeric(String field, String value)
    {
        if (value == null) return true;
        
        if (!value.matches("^[a-zA-Z0-9]+$")) {
            errors.add(field, "The " + field + " may only contain letters and numbers");
            return false;
        }
        return true;
    }
    
    private boolean validateUrl(String field, String value)
    {
        if (value == null) return true;
        
        String urlRegex = "^(https?://)?([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(/.*)?$";
        if (!value.matches(urlRegex)) {
            errors.add(field, "The " + field + " must be a valid URL");
            return false;
        }
        return true;
    }
    
    private boolean validateConfirmed(String field, String value)
    {
        String confirmField = field + "_confirmation";
        String confirmValue = request.queryParams(confirmField);
        
        if (value == null || !value.equals(confirmValue)) {
            errors.add(field, "The " + field + " confirmation does not match");
            return false;
        }
        return true;
    }
    
    private boolean validateUnique(String field, String value, String params)
    {
        if (value == null) return true;
        try {
            String[] parts = params.split(",");
            String table = parts[0];
            String column = parts.length > 1 ? parts[1] : field;
            
            Class<?> modelClass = Class.forName("fr.kainovaii.obsidian.app.models." + capitalize(table));
            
            if (org.javalite.activejdbc.Model.class.isAssignableFrom(modelClass)) {
                @SuppressWarnings("unchecked")
                var model = ((Class<? extends org.javalite.activejdbc.Model>) modelClass)
                    .getDeclaredConstructor().newInstance();
                
                long count = model.count(column + " = ?", value);
                
                if (count > 0) {
                    errors.add(field, "The " + field + " has already been taken");
                    return false;
                }
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Unique validation failed", e);
        }
        
        return true;
    }
    
    private boolean validateIn(String field, String value, String params)
    {
        if (value == null) return true;
        
        String[] allowed = params.split(",");
        for (String option : allowed) {
            if (value.equals(option.trim())) {
                return true;
            }
        }
        
        errors.add(field, "The selected " + field + " is invalid");
        return false;
    }
    
    private boolean validateRegex(String field, String value, String pattern)
    {
        if (value == null) return true;
        
        if (!value.matches(pattern)) {
            errors.add(field, "The " + field + " format is invalid");
            return false;
        }
        return true;
    }
    
    private String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
