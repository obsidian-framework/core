package fr.kainovaii.obsidian.validation;

import java.util.HashMap;
import java.util.Map;

/**
 * Validator for LiveComponent data.
 * Validates Map data directly (from JSON/component state).
 */
public class LiveComponentValidator
{
    /**
     * Validates LiveComponent data with rules.
     * 
     * @param data Data map from component state
     * @param rules Validation rules
     * @return ValidationResult with validated data and errors
     */
    public static ValidationResult validate(Map<String, Object> data, Map<String, String> rules)
    {
        ValidationErrors errors = new ValidationErrors();
        Map<String, Object> validated = new HashMap<>();
        
        for (Map.Entry<String, String> entry : rules.entrySet())
        {
            String field = entry.getKey();
            String rulesString = entry.getValue();
            Object value = data.get(field);
            String strValue = value != null ? value.toString() : null;
            
            String[] rulesList = rulesString.split("\\|");
            
            boolean hasError = false;
            for (String rule : rulesList) {
                if (!validateRule(field, strValue, rule.trim(), errors)) {
                    hasError = true;
                    break;
                }
            }
            
            if (!hasError && value != null) {
                validated.put(field, value);
            }
        }
        
        return new ValidationResult(validated, errors);
    }
    
    /**
     * Validates LiveComponent data, throwing exception on failure.
     * 
     * @param data Data map from component
     * @param rules Validation rules
     * @return Validated data map
     * @throws ValidationException if validation fails
     */
    public static Map<String, Object> validateOrFail(Map<String, Object> data, Map<String, String> rules)
    {
        ValidationResult result = validate(data, rules);
        
        if (result.fails()) {
            throw new ValidationException(result.getErrors());
        }
        
        return result.getData();
    }
    
    private static boolean validateRule(String field, String value, String rule, ValidationErrors errors)
    {
        String[] parts = rule.split(":", 2);
        String ruleName = parts[0];
        String ruleParam = parts.length > 1 ? parts[1] : null;
        
        return switch (ruleName)
        {
            case "required" -> validateRequired(field, value, errors);
            case "email" -> validateEmail(field, value, errors);
            case "min" -> validateMin(field, value, Integer.parseInt(ruleParam), errors);
            case "max" -> validateMax(field, value, Integer.parseInt(ruleParam), errors);
            case "between" -> validateBetween(field, value, ruleParam, errors);
            case "numeric" -> validateNumeric(field, value, errors);
            case "integer" -> validateInteger(field, value, errors);
            case "alpha" -> validateAlpha(field, value, errors);
            case "alphanumeric" -> validateAlphanumeric(field, value, errors);
            case "url" -> validateUrl(field, value, errors);
            case "unique" -> validateUnique(field, value, ruleParam, errors);
            case "in" -> validateIn(field, value, ruleParam, errors);
            case "regex" -> validateRegex(field, value, ruleParam, errors);
            default -> throw new IllegalArgumentException("Unknown validation rule: " + ruleName);
        };
    }
    
    private static boolean validateRequired(String field, String value, ValidationErrors errors)
    {
        if (value == null || value.trim().isEmpty()) {
            errors.add(field, "The " + field + " field is required");
            return false;
        }
        return true;
    }
    
    private static boolean validateEmail(String field, String value, ValidationErrors errors)
    {
        if (value == null) return true;
        
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        if (!value.matches(emailRegex)) {
            errors.add(field, "The " + field + " must be a valid email address");
            return false;
        }
        return true;
    }
    
    private static boolean validateMin(String field, String value, int min, ValidationErrors errors)
    {
        if (value == null) return true;
        
        if (value.length() < min) {
            errors.add(field, "The " + field + " must be at least " + min + " characters");
            return false;
        }
        return true;
    }
    
    private static boolean validateMax(String field, String value, int max, ValidationErrors errors)
    {
        if (value == null) return true;
        
        if (value.length() > max) {
            errors.add(field, "The " + field + " may not be greater than " + max + " characters");
            return false;
        }
        return true;
    }
    
    private static boolean validateBetween(String field, String value, String params, ValidationErrors errors)
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
    
    private static boolean validateNumeric(String field, String value, ValidationErrors errors)
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
    
    private static boolean validateInteger(String field, String value, ValidationErrors errors)
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
    
    private static boolean validateAlpha(String field, String value, ValidationErrors errors)
    {
        if (value == null) return true;
        
        if (!value.matches("^[a-zA-Z]+$")) {
            errors.add(field, "The " + field + " may only contain letters");
            return false;
        }
        return true;
    }
    
    private static boolean validateAlphanumeric(String field, String value, ValidationErrors errors)
    {
        if (value == null) return true;
        
        if (!value.matches("^[a-zA-Z0-9]+$")) {
            errors.add(field, "The " + field + " may only contain letters and numbers");
            return false;
        }
        return true;
    }
    
    private static boolean validateUrl(String field, String value, ValidationErrors errors)
    {
        if (value == null) return true;
        
        String urlRegex = "^(https?://)?([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(/.*)?$";
        if (!value.matches(urlRegex)) {
            errors.add(field, "The " + field + " must be a valid URL");
            return false;
        }
        return true;
    }
    
    private static boolean validateUnique(String field, String value, String params, ValidationErrors errors)
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
    
    private static boolean validateIn(String field, String value, String params, ValidationErrors errors)
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
    
    private static boolean validateRegex(String field, String value, String pattern, ValidationErrors errors)
    {
        if (value == null) return true;
        
        if (!value.matches(pattern)) {
            errors.add(field, "The " + field + " format is invalid");
            return false;
        }
        return true;
    }
    
    private static String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
