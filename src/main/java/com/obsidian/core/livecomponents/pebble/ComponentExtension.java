package com.obsidian.core.livecomponents.pebble;

import com.obsidian.core.livecomponents.core.ComponentManager;
import com.obsidian.core.livecomponents.http.RequestContext;
import com.obsidian.core.livecomponents.session.SessionContext;
import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pebble extension for LiveComponents.
 * Registers the {@code component()} function for mounting server-side reactive components in templates.
 */
public class ComponentExtension extends AbstractExtension
{
    /** Component manager instance */
    private final ComponentManager componentManager;

    /**
     * Constructs a new ComponentExtension.
     *
     * @param componentManager Component manager
     */
    public ComponentExtension(ComponentManager componentManager) {
        this.componentManager = componentManager;
    }

    /**
     * Registers the {@code component()} function.
     *
     * @return Map of function name to implementation
     */
    @Override
    public Map<String, Function> getFunctions()
    {
        Map<String, Function> functions = new HashMap<>();
        functions.put("component", new ComponentFunction(componentManager));
        return functions;
    }

    /**
     * Pebble function for mounting LiveComponents in templates.
     * Usage: {@code {{ component('MyComponent') | raw }}}
     */
    private static class ComponentFunction implements Function
    {
        /** Component manager instance */
        private final ComponentManager componentManager;

        /**
         * Constructs a new ComponentFunction.
         *
         * @param componentManager Component manager
         */
        public ComponentFunction(ComponentManager componentManager) {
            this.componentManager = componentManager;
        }

        /**
         * Executes component mounting.
         * Retrieves the current session and request from thread-local contexts,
         * then delegates to {@link ComponentManager#mount(String, spark.Session, spark.Request)}.
         *
         * @param args       Function arguments — expects component name as first positional arg
         * @param self       Current Pebble template instance
         * @param context    Evaluation context containing all template variables
         * @param lineNumber Line number in the template where the function is called
         * @return Rendered component HTML string, or an HTML comment on error
         */
        @Override
        public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber)
        {
            String componentName = (String) args.get("0");
            if (componentName == null || componentName.isEmpty()) {
                return "<!-- Error: component name required -->";
            }

            try {
                spark.Session session = SessionContext.get();
                spark.Request request = RequestContext.get();
                return componentManager.mount(componentName, session, request);
            } catch (Exception e) {
                return "<!-- Error loading component '" + componentName + "': " + e.getMessage() + " -->";
            }
        }

        /**
         * Returns the argument names accepted by this function.
         *
         * @return List containing {@code "componentName"}
         */
        @Override
        public List<String> getArgumentNames() {
            List<String> names = new ArrayList<>();
            names.add("componentName");
            return names;
        }
    }
}