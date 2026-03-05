package fr.kainovaii.obsidian.livecomponents.pebble;

import fr.kainovaii.obsidian.core.Obsidian;
import fr.kainovaii.obsidian.livecomponents.http.RequestContext;
import fr.kainovaii.obsidian.livecomponents.session.SessionContext;
import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Pebble extension for LiveComponents.
 * Provides the {@code component()} template function for mounting server-side reactive components.
 */
public class ComponentHelperExtension extends AbstractExtension
{
    /** Logger instance */
    private static final Logger logger = Logger.getLogger(ComponentHelperExtension.class.getName());

    /**
     * Registers the {@code component()} function.
     *
     * @return Map of function name to implementation
     */
    @Override
    public Map<String, Function> getFunctions() {
        Map<String, Function> functions = new HashMap<>();
        functions.put("component", new ComponentFunction());
        return functions;
    }

    /**
     * Pebble function for mounting LiveComponents in templates.
     * Usage: {@code {{ component('MyComponent') | raw }}}
     */
    private static class ComponentFunction implements Function
    {
        /**
         * Executes component mounting.
         * Retrieves the current session and request from thread-local contexts,
         * then delegates mounting to {@link fr.kainovaii.obsidian.livecomponents.core.ComponentManager}.
         *
         * @param args       Function arguments — expects component name as first positional arg
         * @param self       Current Pebble template instance
         * @param context    Evaluation context containing all template variables
         * @param lineNumber Line number in the template where the function is called
         * @return Rendered component HTML string
         * @throws RuntimeException if the component name is missing or mounting fails
         */
        @Override
        public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber)
        {
            String componentName = null;

            if (args.containsKey("0")) {
                componentName = (String) args.get("0");
            } else if (args.containsKey("componentName")) {
                componentName = (String) args.get("componentName");
            }

            if (componentName == null || componentName.isEmpty()) {
                throw new RuntimeException("LiveComponent error: component name is required. Available args: " + args.keySet());
            }

            logger.info("Mounting component: " + componentName);

            spark.Session session = SessionContext.get();
            spark.Request request = RequestContext.get();

            String result = Obsidian.getComponentManager().mount(componentName, session, request);

            logger.info("Component mounted successfully: " + componentName);
            return result;
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