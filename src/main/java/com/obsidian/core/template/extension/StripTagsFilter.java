package com.obsidian.core.template.extension;

import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.List;
import java.util.Map;

public class StripTagsFilter extends AbstractExtension
{
    @Override
    public Map<String, Filter> getFilters()
    {
        return Map.of("striptags", new Filter()
        {
            @Override
            public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber)
            {
                if (input == null) return "";
                return input.toString().replaceAll("<[^>]*>", "");
            }

            @Override
            public List<String> getArgumentNames() {
                return null;
            }
        });
    }
}