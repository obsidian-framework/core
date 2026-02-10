package fr.kainovaii.core.security.csrf;

import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import spark.Request;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CsrfExtension extends AbstractExtension
{
    @Override
    public Map<String, Function> getFunctions()
    {
        Map<String, Function> functions = new HashMap<>();
        functions.put("csrf_field", new CsrfFieldFunction());
        functions.put("csrf_token", new CsrfTokenFunction());
        return functions;
    }

    private static class CsrfFieldFunction implements Function
    {
        @Override
        public Object execute(Map<String, Object> args, PebbleTemplate self,
                              EvaluationContext context, int lineNumber) {
            Request req = (Request) context.getVariable("request");
            if (req == null) return "";

            String token = CsrfProtection.getToken(req);
            return "<input type=\"hidden\" name=\"_csrf\" value=\"" + token + "\">";
        }

        @Override
        public List<String> getArgumentNames() {
            return List.of();
        }
    }

    private static class CsrfTokenFunction implements Function
    {
        @Override
        public Object execute(Map<String, Object> args, PebbleTemplate self,
                              EvaluationContext context, int lineNumber) {
            Request req = (Request) context.getVariable("request");
            if (req == null) return "";

            return CsrfProtection.getToken(req);
        }

        @Override
        public List<String> getArgumentNames() {
            return List.of();
        }
    }
}