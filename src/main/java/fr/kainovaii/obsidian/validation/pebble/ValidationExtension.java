package fr.kainovaii.obsidian.validation.pebble;

import fr.kainovaii.obsidian.validation.ValidationErrors;
import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pebble extension for validation helpers.
 * Provides error() and old() functions for forms.
 */
public class ValidationExtension extends AbstractExtension
{
    /**
     * Registers validation functions.
     * 
     * @return Map of function names to implementations
     */
    @Override
    public Map<String, Function> getFunctions() {
        Map<String, Function> functions = new HashMap<>();
        functions.put("error", new ErrorFunction());
        functions.put("old", new OldFunction());
        return functions;
    }
    
    /**
     * Gets error for field.
     * Usage: {{ error('email') }}
     */
    private static class ErrorFunction implements Function
    {
        @Override
        public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
            String field = (String) args.get("0");
            if (field == null) return "";
            
            ValidationErrors errors = (ValidationErrors) context.getVariable("errors");
            if (errors == null) return "";
            
            String error = errors.get(field);
            return error != null ? error : "";
        }
        
        @Override
        public List<String> getArgumentNames() {
            List<String> names = new ArrayList<>();
            names.add("field");
            return names;
        }
    }
    
    /**
     * Gets old input value.
     * Usage: {{ old('email') }}
     */
    private static class OldFunction implements Function
    {
        @Override
        public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
            String field = (String) args.get("0");
            if (field == null) return "";
            
            @SuppressWarnings("unchecked")
            Map<String, String[]> old = (Map<String, String[]>) context.getVariable("old");
            if (old == null) return "";
            
            String[] values = old.get(field);
            return values != null && values.length > 0 ? values[0] : "";
        }
        
        @Override
        public List<String> getArgumentNames() {
            List<String> names = new ArrayList<>();
            names.add("field");
            return names;
        }
    }
}
