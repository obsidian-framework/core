package fr.kainovaii.obsidian.core;

import fr.kainovaii.obsidian.config.ConfigLoader;
import fr.kainovaii.obsidian.database.DB;
import fr.kainovaii.obsidian.database.DatabaseLoader;
import fr.kainovaii.obsidian.database.MigrationManager;
import fr.kainovaii.obsidian.di.ComponentScanner;
import fr.kainovaii.obsidian.di.Container;
import fr.kainovaii.obsidian.livecomponents.core.ComponentManager;
import fr.kainovaii.obsidian.livecomponents.pebble.ComponentExtension;
import fr.kainovaii.obsidian.livecomponents.scanner.LiveComponentScanner;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.ClasspathLoader;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * Main class of the Obsidian framework.
 * Manages initialization and configuration of all framework components.
 */
public class Obsidian
{
    /** Logger used by Spark */
    public final static Logger logger = LoggerFactory.getLogger(Obsidian.class);
    /** Base package for component scanning */
    private static String basePackage;

    /** LiveComponents manager */
    private static ComponentManager componentManager;

    /**
     * Default constructor.
     * Initializes base package to "fr.kainovaii.obsidian.app".
     */
    public Obsidian()
    {
        basePackage = "fr.kainovaii.obsidian.app";
    }

    /**
     * Constructor with main class.
     * Automatically determines base package from provided class.
     *
     * @param mainClass The application's main class
     */
    public Obsidian(Class<?> mainClass)
    {
        basePackage = mainClass.getPackage().getName();
    }

    /**
     * Sets the base package for component scanning.
     *
     * @param basePackage The base package
     * @return Current instance for chaining
     */
    public Obsidian setBasePackage(String basePackage)
    {
        Obsidian.basePackage = basePackage;
        return this;
    }

    /**
     * Gets the configured base package.
     *
     * @return The base package
     */
    public static String getBasePackage()
    {
        return Obsidian.basePackage;
    }

    /**
     * Initializes database connection.
     * Supports SQLite, MySQL and PostgreSQL based on configuration.
     */
    public void connectDatabase() { DatabaseLoader.loadDatabase(); }

    /**
     * Loads and executes database migrations.
     */
    public void loadMigrations()
    {
        MigrationManager migrations = new MigrationManager(DB.getInstance(), logger);
        migrations.discover();
        migrations.migrate();
    }

    /**
     * Initializes dependency injection container.
     * Scans base package to discover components.
     */
    public void loadContainer() { ComponentScanner.scanPackage(); }

    /**
     * Loads and executes application configurations.
     * Discovers @Config annotated classes and calls their configure() methods in priority order.
     */
    public void loadConfig() { ConfigLoader.loadConfigurations(); }

    /**
     * Initializes LiveComponents system.
     * Configures Pebble and registers components in container.
     */
    public void loadLiveComponents()
    {
        System.out.println("Loading LiveComponents...");

        try {
            ClasspathLoader loader = new ClasspathLoader();
            PebbleEngine componentPebble = new PebbleEngine.Builder()
                    .loader(loader)
                    .cacheActive(true)
                    .build();

            componentManager = new ComponentManager(componentPebble);

            LiveComponentScanner.scan(basePackage, componentManager);

            componentPebble = new PebbleEngine.Builder()
                    .loader(loader)
                    .extension(new ComponentExtension(componentManager))
                    .cacheActive(true)
                    .build();

            java.lang.reflect.Field field = ComponentManager.class.getDeclaredField("pebbleEngine");
            field.setAccessible(true);
            field.set(componentManager, componentPebble);

            Container.singleton(ComponentManager.class, componentManager);

            System.out.println("LiveComponents loaded successfully!");
        } catch (Exception e) {
            logger.error("Failed to load LiveComponents: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Loads configuration and environment variables.
     *
     * @return EnvLoader instance containing configuration
     */
    public static EnvLoader loadConfigAndEnv()
    {
        EnvLoader env = new EnvLoader();
        env.load();
        return env;
    }

    /**
     * Starts the web server.
     */
    public void startWebServer() { new WebServer().start(); }

    /**
     * Gets web server port from configuration.
     *
     * @return Configured port for web server
     */
    public static int getWebPort() { return Integer.parseInt(Obsidian.loadConfigAndEnv().get("PORT_WEB")); }

    /**
     * Displays startup message (MOTD) in console.
     */
    public void registerMotd()
    {
        EnvLoader env = new EnvLoader();

        env.load();
        final String RESET = "\u001B[0m";
        final String CYAN = "\u001B[36m";
        final String GREEN = "\u001B[32m";

        System.out.println(CYAN + "+--------------------------------------+" + RESET);
        System.out.println(CYAN + "|          Obsidian 1.0                |" + RESET);
        System.out.println(CYAN + "+--------------------------------------+" + RESET);
        System.out.println(GREEN + "| Developer         : KainoVaii        |" + RESET);
        System.out.println(GREEN + "| Version           : 1.0              |" + RESET);
        System.out.println(GREEN + "| Environment       : " + env.get("ENVIRONMENT") + "              |" + RESET);
        System.out.println(CYAN + "+--------------------------------------+" + RESET);
        System.out.println(CYAN + "|      Loading modules...              |" + RESET);
        System.out.println(CYAN + "+--------------------------------------+" + RESET);
        System.out.println();
    }

    /**
     * Initializes all framework components in order.
     * Sequence: MOTD → Config → Database → Migrations → Container → LiveComponents → WebServer
     */
    /**
     * Gets the LiveComponents manager.
     *
     * @return ComponentManager instance
     */
    public static ComponentManager getComponentManager() {
        return componentManager;
    }

    public void init()
    {
        registerMotd();
        loadConfigAndEnv();
        loadMigrations();
        connectDatabase();
        loadContainer();
        loadLiveComponents();
        loadConfig();
        startWebServer();
    }

    /**
     * Main entry point to start Obsidian application.
     *
     * @param mainClass The application's main class
     * @return Initialized Obsidian instance
     */
    public static Obsidian run(Class<?> mainClass)
    {
        Obsidian app = new Obsidian(mainClass);
        app.init();
        return app;
    }
}