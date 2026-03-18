package com.obsidian.core.security.csrf.pebble;

import com.obsidian.core.security.csrf.CsrfProtection;
import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import spark.Request;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pebble extension for CSRF protection.
 * Registers csrf_field() and csrf_token() functions for templates.
 */
public class CsrfExtension extends AbstractExtension
{
    /**
     * Registers CSRF functions.
     *
     * @return Map of function names to implementations
     */
    @Override
    public Map<String, Function> getFunctions()
    {
        Map<String, Function> functions = new HashMap<>();
        functions.put("csrf_field", new CsrfFieldFunction());
        functions.put("csrf_token", new CsrfTokenFunction());
        return functions;
    }

    /**
     * Pebble function for generating CSRF hidden input field.
     * Usage: {{ csrf_field() }}
     */
    private static class CsrfFieldFunction implements Function
    {
        /**
         * Generates hidden input field with CSRF token.
         *
         * @param args Function arguments (none)
         * @param self Template instance
         * @param context Evaluation context containing request
         * @param lineNumber Line number in template
         * @return HTML hidden input field or empty string if no request
         */
        @Override
        public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber)
        {
            Request req = (Request) context.getVariable("request");
            if (req == null) return "";

            String token = CsrfProtection.getToken(req);
            return "<input type=\"hidden\" name=\"_csrf\" value=\"" + token + "\">";
        }

        /**
         * Returns argument names (none required).
         *
         * @return Empty list
         */
        @Override
        public List<String> getArgumentNames() {
            return List.of();
        }
    }

    /**
     * Pebble function for getting CSRF token value.
     * Usage: {{ csrf_token() }}
     */
    private static class CsrfTokenFunction implements Function
    {
        /**
         * Gets CSRF token value.
         *
         * @param args Function arguments (none)
         * @param self Template instance
         * @param context Evaluation context containing request
         * @param lineNumber Line number in template
         * @return CSRF token or empty string if no request
         */
        @Override
        public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber)
        {
            Request req = (Request) context.getVariable("request");
            if (req == null) return "";

            return CsrfProtection.getToken(req);
        }

        /**
         * Returns argument names (none required).
         *
         * @return Empty list
         */
        @Override
        public List<String> getArgumentNames() {
            return List.of();
        }
    }
}