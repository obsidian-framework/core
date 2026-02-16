package fr.kainovaii.obsidian.template;

import fr.kainovaii.obsidian.livecomponents.pebble.LiveComponentsScriptExtension;
import fr.kainovaii.obsidian.routing.pebble.RouteExtension;
import fr.kainovaii.obsidian.security.csrf.pebble.CsrfExtension;
import fr.kainovaii.obsidian.livecomponents.pebble.ComponentHelperExtension;
import fr.kainovaii.obsidian.flash.pebble.FlashExtension;
import fr.kainovaii.obsidian.template.extension.StripTagsFilter;
import fr.kainovaii.obsidian.validation.pebble.ValidationExtension;
import spark.ModelAndView;
import spark.TemplateEngine;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.ClasspathLoader;

import java.io.StringWriter;
import java.util.Map;

/**
 * Pebble template engine integration for Spark.
 * Provides template rendering with custom extensions and caching.
 */
public class PebbleTemplateEngine extends TemplateEngine
{
    /** Pebble engine instance */
    private final PebbleEngine engine;

    /**
     * Constructor.
     * Initializes Pebble with classpath loader and registers extensions.
     */
    public PebbleTemplateEngine()
    {
        ClasspathLoader loader = new ClasspathLoader();
        engine = new PebbleEngine.Builder()
                .loader(loader)
                .extension(new RouteExtension())
                .extension(new StripTagsFilter())
                .extension(new CsrfExtension())
                .extension(new FlashExtension())
                .extension(new ComponentHelperExtension())
                .extension(new ValidationExtension())
                .extension(new LiveComponentsScriptExtension())
                .cacheActive(true)
                .build();
    }

    /**
     * Renders template from ModelAndView.
     *
     * @param modelAndView Model and view name
     * @return Rendered HTML
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
     * Renders template with model data.
     *
     * @param templateName Template name/path
     * @param model Template variables
     * @return Rendered HTML
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