package com.obsidian.core.routing.pebble;

import com.obsidian.core.routing.Route;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pebble function for generating route URLs.
 * Usage: {{ route(name='home.index') }}
 */
class RouteFunction implements Function
{
    /**
     * Executes route function to get path for named route.
     *
     * @param args Function arguments containing route name
     * @param self Template instance
     * @param context Evaluation context
     * @param lineNumber Line number in template
     * @return Route path or empty string if not found
     */
    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber)
    {
        String routeName = (String) args.get("name");
        if (routeName == null || routeName.isEmpty()) {
            return "";
        }
        return Route.getPath(routeName);
    }

    /**
     * Returns argument names for function.
     *
     * @return List containing "name"
     */
    @Override
    public List<String> getArgumentNames()
    {
        List<String> names = new ArrayList<>();
        names.add("name");
        return names;
    }
}