package com.obsidian.core.template;

import com.obsidian.core.core.EnvKeys;
import com.obsidian.core.core.Obsidian;
import com.obsidian.core.livecomponents.pebble.ComponentTag;
import com.obsidian.core.livecomponents.pebble.ComponentTagExtension;
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
import spark.TemplateEngine;

import java.io.StringWriter;
import java.util.Map;

/**
 * Pebble template engine integration for Spark.
 * Provides template rendering with custom extensions and environment-aware configuration.
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
        boolean isDev = Obsidian.loadConfigAndEnv().get(EnvKeys.ENVIRONMENT).equalsIgnoreCase("DEV");

        System.out.println("Building PebbleEngine");
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
                .extension(new ComponentTagExtension())
                .cacheActive(!isDev);

        if (isDev) {
            String prefix = System.getProperty("user.dir") + "/src/main/resources/";
            FileLoader loader = new FileLoader(prefix);
            builder.loader(loader);
            builder.extension(new LiveReloadScriptExtension());
        } else {
            builder.loader(new ClasspathLoader());
        }

        engine = builder.build();

        System.out.println("PebbleEngine built with extensions");
    }

    /**
     * Renders a template from a {@link ModelAndView} object.
     * The view name is used to locate the template, and the model provides
     * the variables available during rendering.
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
            template.evaluate(writer, (Map<String, Object>) modelAndView.getModel());
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Renders a template by name with the provided model data.
     * Useful for rendering templates outside of the standard Spark route context.
     *
     * @param templateName The path to the template relative to the resources root
     * @param model        A map of variables to expose during template rendering
     * @return The rendered HTML as a string
     * @throws RuntimeException If template evaluation fails
     */
    public String render(String templateName, Map<String, Object> model)
    {
        try {
            var template = engine.getTemplate(templateName);
            var writer = new StringWriter();
            template.evaluate(writer, model);
            String html = writer.toString();
            return html.replace("</body>", getScriptTag() + "</body>");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private String getScriptTag()
    {
        String env = System.getenv(EnvKeys.ENVIRONMENT);
        String version = "production".equalsIgnoreCase(env) ? "1.0.0" : String.valueOf(System.currentTimeMillis());
        return "<script src=\"/obsidian/livecomponents.js?v=" + version + "\"></script>\n";
    }
}