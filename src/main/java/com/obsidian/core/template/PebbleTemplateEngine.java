package com.obsidian.core.template;

import com.obsidian.core.core.Obsidian;
import com.obsidian.core.error.ErrorHandler;
import com.obsidian.core.livereload.LiveReloadScriptExtension;
import com.obsidian.core.routing.pebble.RouteExtension;
import com.obsidian.core.security.csrf.pebble.CsrfExtension;
import com.obsidian.core.livecomponents.pebble.ComponentHelperExtension;
import com.obsidian.core.flash.pebble.FlashExtension;
import com.obsidian.core.template.extension.FlowScriptExtension;
import com.obsidian.core.template.extension.MarkdownFilter;
import com.obsidian.core.template.extension.MarkdownTag;
import com.obsidian.core.template.extension.StripTagsFilter;
import com.obsidian.core.validation.pebble.ValidationExtension;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.ClasspathLoader;
import io.pebbletemplates.pebble.loader.FileLoader;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.TemplateEngine;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Pebble template engine integration for Spark.
 * Provides template rendering with custom extensions and environment-aware configuration.
 * Global variables from {@link TemplateManager} are automatically merged into every render call.
 */
public class PebbleTemplateEngine extends TemplateEngine
{
    /** Pebble engine instance */
    private final PebbleEngine engine;

    /**
     * Initializes the Pebble engine with the appropriate loader, extensions,
     * and caching strategy based on the current environment.
     */
    public PebbleTemplateEngine()
    {
        boolean isDev = Obsidian.loadConfigAndEnv().get("ENVIRONMENT").equalsIgnoreCase("DEV");

        PebbleEngine.Builder builder = new PebbleEngine.Builder()
            .extension(new RouteExtension())
            .extension(new StripTagsFilter())
            .extension(new CsrfExtension())
            .extension(new FlashExtension())
            .extension(new ComponentHelperExtension())
            .extension(new ValidationExtension())
            .extension(new FlowScriptExtension())
            .extension(new MarkdownFilter())
            .extension(new MarkdownTag())
            .cacheActive(!isDev);

        if (isDev) {
            String prefix = System.getProperty("user.dir") + "/src/main/resources/";
            builder.loader(new FileLoader(prefix));
            builder.extension(new LiveReloadScriptExtension());
        } else {
            builder.loader(new ClasspathLoader());
        }

        engine = builder.build();
    }

    /**
     * Renders a template from a {@link ModelAndView} object.
     * Global variables from {@link TemplateManager} are merged into the model,
     * with model values taking precedence over globals.
     *
     * @param modelAndView The model and view name to render
     * @return The rendered HTML as a string
     * @throws RuntimeException If template evaluation fails
     */
    @Override
    public String render(ModelAndView modelAndView)
    {
        try {
            var template = engine.getTemplate(modelAndView.getViewName());
            var writer = new StringWriter();
            template.evaluate(writer, mergeWithGlobals((Map<String, Object>) modelAndView.getModel()));
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Renders a template by name with the provided model data.
     * Global variables from {@link TemplateManager} are merged into the model,
     * with model values taking precedence over globals.
     *
     * @param templateName The path to the template relative to the resources root
     * @param model        A map of variables to expose during template rendering
     * @return The rendered HTML as a string
     * @throws RuntimeException If template evaluation fails
     */
    protected String render(String templateName, Map<String, Object> model)
    {
        Map<String, Object> merged = new HashMap(TemplateManager.getGlobals());
        if (model != null) {
            merged.putAll(model);
        }

        try {
            String html = TemplateManager.get().render("view/" + templateName, merged);
            return injectLiveComponentsScript(html);
        } catch (Exception exception) {
            Request req = (Request) merged.get("request");
            Response res = (Response) merged.get("response");
            return ErrorHandler.handle(exception, req, res);
        }
    }

    /**
     * Merges global template variables with the provided model.
     * Model values take precedence over globals in case of key conflicts.
     *
     * @param model The route-specific model
     * @return A new map containing globals + model
     */
    private Map<String, Object> mergeWithGlobals(Map<String, Object> model)
    {
        Map<String, Object> merged = new HashMap<>(TemplateManager.getGlobals());
        merged.putAll(model);
        return merged;
    }

    private String injectLiveComponentsScript(String html)
    {
        if (html == null || !html.contains("</body>")) return html;
        String env = System.getenv("ENVIRONMENT");
        String version = "production".equalsIgnoreCase(env) ? "1.0.0" : String.valueOf(System.currentTimeMillis());
        String script = "<script src=\"/obsidian/livecomponents.js?v=" + version + "\"></script>\n";
        return html.replace("</body>", script + "</body>");
    }
}