package com.obsidian.core.livereload;

import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.List;
import java.util.Map;

/**
 * Pebble extension that provides the {% obsidianDevTools() %} function.
 * Injects the live reload script in dev mode — outputs nothing in production.
 *
 * Usage in templates: {{ obsidianDevTools() }}
 * Typically placed just before </body> in the base layout.
 */
public class LiveReloadScriptExtension extends AbstractExtension
{
    private static final String SCRIPT = """
        <script>
        (function() {
            if (typeof EventSource === 'undefined') return;
            if (window.__obsidianSSE) {
                window.__obsidianSSE.close();
            }
            var es = new EventSource('/__obsidian/livereload');
            window.__obsidianSSE = es;
            es.onmessage = function(e) {
                if (e.data === 'reload') {
                    es.close();
                    window.location.reload();
                }
            };
            es.onerror = function() {
                es.close();
            };
            console.log('[Obsidian] Live reload connected.');
        })();
        </script>
    """;

    @Override
    public Map<String, Function> getFunctions()
    {
        return Map.of("obsidianDevTools", new Function() {
            @Override
            public Object execute(Map<String, Object> args, PebbleTemplate self,
                                  EvaluationContext context, int lineNumber)
            {
                return SCRIPT;
            }

            @Override
            public List<String> getArgumentNames()
            {
                return List.of();
            }
        });
    }
}
