package fr.kainovaii.obsidian.livecomponents.pebble;

import fr.kainovaii.obsidian.core.Obsidian;
import fr.kainovaii.obsidian.livecomponents.ComponentException;
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
 * Alternative Pebble extension for LiveComponents with detailed logging.
 * Provides component() function with comprehensive error handling and logging.
 */
public class ComponentHelperExtension extends AbstractExtension
{
    /** Logger instance */
    private static final Logger logger = Logger.getLogger(ComponentHelperExtension.class.getName());

    /**
     * Registers component function.
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
     * Pebble function for mounting LiveComponents.
     * Usage: {{ component('counter') }}
     */
    private static class ComponentFunction implements Function
    {
        /**
         * Executes component mounting.
         * Exceptions are rethrown as RuntimeException to bubble up to Spark's global exception handler.
         *
         * @param args Function arguments (component name)
         * @param self Template instance
         * @param context Evaluation context
         * @param lineNumber Line number in template
         * @return Rendered component HTML
         */
        @Override
        public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
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
            String result = Obsidian.getComponentManager().mount(componentName, session);

            logger.info("Component mounted successfully: " + componentName);
            return result;
        }

        /**
         * Returns argument names for function.
         *
         * @return List containing "componentName"
         */
        @Override
        public List<String> getArgumentNames() {
            List<String> names = new ArrayList<>();
            names.add("componentName");
            return names;
        }
    }
}