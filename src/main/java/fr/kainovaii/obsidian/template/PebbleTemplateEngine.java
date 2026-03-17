package fr.kainovaii.obsidian.template;

import fr.kainovaii.obsidian.core.Obsidian;
import fr.kainovaii.obsidian.livecomponents.pebble.LiveComponentsScriptExtension;
import fr.kainovaii.obsidian.livereload.LiveReloadScriptExtension;
import fr.kainovaii.obsidian.routing.pebble.RouteExtension;
import fr.kainovaii.obsidian.security.csrf.pebble.CsrfExtension;
import fr.kainovaii.obsidian.livecomponents.pebble.ComponentHelperExtension;
import fr.kainovaii.obsidian.flash.pebble.FlashExtension;
import fr.kainovaii.obsidian.template.extension.FlowScriptExtension;
import fr.kainovaii.obsidian.template.extension.MarkdownFilter;
import fr.kainovaii.obsidian.template.extension.MarkdownTag;
import fr.kainovaii.obsidian.template.extension.StripTagsFilter;
import fr.kainovaii.obsidian.validation.pebble.ValidationExtension;
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
        boolean isDev = Obsidian.loadConfigAndEnv().get("ENVIRONMENT").equalsIgnoreCase("DEV");

        PebbleEngine.Builder builder = new PebbleEngine.Builder()
                .extension(new RouteExtension())
                .extension(new StripTagsFilter())
                .extension(new CsrfExtension())
                .extension(new FlashExtension())
                .extension(new ComponentHelperExtension())
                .extension(new ValidationExtension())
                .extension(new LiveComponentsScriptExtension())
                .extension(new FlowScriptExtension())
                .extension(new MarkdownFilter())
                .extension(new MarkdownTag())
                .cacheActive(!isDev);

        if (isDev) {
            FileLoader loader = new FileLoader();
            loader.setPrefix(System.getProperty("user.dir") + "/src/main/resources/");
            builder.loader(loader);
            builder.extension(new LiveReloadScriptExtension());
        } else {
            builder.loader(new ClasspathLoader());
        }

        engine = builder.build();
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
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}