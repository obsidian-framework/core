package fr.kainovaii.obsidian.template.extension;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.extension.escaper.SafeString;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Pebble extension that provides a {@code markdown} filter for rendering
 * GitHub Flavored Markdown (GFM) content to HTML.
 */
public class MarkdownFilter extends AbstractExtension
{
    private static final Parser PARSER;
    private static final HtmlRenderer RENDERER;

    static
    {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, Arrays.asList(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListExtension.create(),
                AutolinkExtension.create()
        ));
        PARSER = Parser.builder(options).build();
        RENDERER = HtmlRenderer.builder(options).build();
    }

    /**
     * Converts a Markdown string to HTML.
     *
     * @param markdown the Markdown content to render
     * @return the rendered HTML string
     */
    public static String render(String markdown)
    {
        return RENDERER.render(PARSER.parse(markdown));
    }

    /**
     * Returns the {@code markdown} filter implementation.
     *
     * @return a map with the filter name as key and its {@link Filter} implementation as value
     */
    @Override
    public Map<String, Filter> getFilters()
    {
        return Map.of("markdown", new Filter()
        {
            /**
             * Converts a Markdown string to HTML.
             *
             * @param input      the Markdown content, or {@code null}
             * @param args       unused filter arguments
             * @param self       the current Pebble template
             * @param context    the evaluation context
             * @param lineNumber the line number in the template
             * @return the rendered HTML as a {@link SafeString}, or an empty string if input is {@code null}
             */
            @Override
            public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber)
            {
                if (input == null) return "";
                return new SafeString(render(input.toString()));
            }

            @Override
            public List<String> getArgumentNames() {
                return null;
            }
        });
    }
}