package com.obsidian.core.validation.pebble;

import com.obsidian.core.validation.ValidationErrors;
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
        public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber)
        {
            String field = (String) args.get("field");
            if (field == null) return "";

            Object errorsObj = context.getVariable("errors");
            if (errorsObj == null) return "";

            ValidationErrors errors = (ValidationErrors) errorsObj;
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
        public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber)
        {
            String field = (String) args.get("field");
            if (field == null) return "";

            Object oldObj = context.getVariable("old");
            if (oldObj == null) return "";

            if (oldObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, ?> old = (Map<String, ?>) oldObj;

                Object value = old.get(field);

                if (value instanceof String[]) {
                    String[] values = (String[]) value;
                    return values.length > 0 ? values[0] : "";
                }

                if (value instanceof String) {
                    return value;
                }
            }

            return "";
        }

        @Override
        public List<String> getArgumentNames() {
            List<String> names = new ArrayList<>();
            names.add("field");
            return names;
        }
    }
}