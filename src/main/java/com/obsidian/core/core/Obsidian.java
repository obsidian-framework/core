package com.obsidian.core.core;

import com.obsidian.core.cache.CacheLoader;
import com.obsidian.core.cli.Cli;
import com.obsidian.core.config.ConfigLoader;
import com.obsidian.core.database.DB;
import com.obsidian.core.database.DatabaseLoader;
import com.obsidian.core.database.MigrationManager;
import com.obsidian.core.database.seeder.SeederLoader;
import com.obsidian.core.di.ComponentScanner;
import com.obsidian.core.di.ServiceProviderLoader;
import com.obsidian.core.livecomponents.core.ComponentManager;
import com.obsidian.core.livecomponents.core.LiveComponentsLoader;
import com.obsidian.core.storage.StorageLoader;
import com.obsidian.core.template.TemplateManager;
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

    private String[] args = new String[0];

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
    public Obsidian(Class<?> mainClass, String[] args)
    {
        basePackage = mainClass.getPackage().getName();
        this.args = args;
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
     * Loads and executes database seeders.
     * Discovers @Seeder annotated classes and calls their seed() methods in priority order.
     * Must be called after database initialization and migrations.
     */
    public void loadSeeders() { SeederLoader.loadSeeders(); }

    /**
     * Initializes LiveComponents system.
     * Configures Pebble and registers components in container.
     */
    public void loadLiveComponents() { LiveComponentsLoader.loadLiveComponents(); }

    /**
     * Loads environment variables once and returns the singleton instance.
     *
     * @return EnvLoader singleton
     */
    public static EnvLoader loadConfigAndEnv()
    {
        EnvLoader env = new EnvLoader();
        env.load();
        return EnvLoader.getInstance();
    }

    public void startCli() {
        Cli.handle(args);
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
    public static int getWebPort() { return Integer.parseInt(EnvLoader.getInstance().get("PORT_WEB")); }

    /**
     * Initializes cache system.
     * Configures driver based on CACHE_DRIVER environment variable (memory or redis).
     */
    public void loadCache() { CacheLoader.loadCache(); }


    private void loadLiveComponentsScript()
    {
        String env = System.getenv("ENVIRONMENT");
        String version = "production".equalsIgnoreCase(env) ? "1.0.0" : String.valueOf(System.currentTimeMillis());
        TemplateManager.setGlobal("livecomponents_scripts", "<script src=\"/obsidian/livecomponents.js?v=" + version + "\"></script>\n");
        logger.info("LiveComponents script injected with version: {}", version); // 👈
    }

    /**
     * Displays startup message (MOTD) in console.
     */
    public void registerMotd()
    {
        EnvLoader env = EnvLoader.getInstance();
        final String RESET = "\u001B[0m";
        final String CYAN = "\u001B[36m";
        final String GREEN = "\u001B[32m";
        System.out.println(CYAN + "+--------------------------------------+" + RESET);
        System.out.println(CYAN + "|               Obsidian               |" + RESET);
        System.out.println(CYAN + "+--------------------------------------+" + RESET);
        System.out.println(GREEN + "| Version           : 1.1.0            |" + RESET);
        System.out.println(GREEN + "| Environment       : " + env.get("ENVIRONMENT") + "              |" + RESET);
        System.out.println(GREEN + "| Web Port          : " + env.get("PORT_WEB")+ "             |" + RESET);
        System.out.println(CYAN + "+--------------------------------------+" + RESET);
        System.out.println(CYAN + "|           Loading modules...         |" + RESET);
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
        return LiveComponentsLoader.getComponentManager();
    }

    private void loadStorage() { StorageLoader.loadStorage(); }

    private void loadServiceProvider() { ServiceProviderLoader.load(); }

    public void init()
    {
        loadConfigAndEnv();
        registerMotd();
        loadConfig();
        connectDatabase();
        loadMigrations();
        loadSeeders();
        loadContainer();
        loadStorage();
        loadServiceProvider();
        startWebServer();
        loadLiveComponents();
        loadLiveComponentsScript();
        loadCache();
    }


    /**
     * Main entry point to start Obsidian application.
     *
     * @param mainClass The application's main class
     * @return Initialized Obsidian instance
     */
    public static Obsidian run(Class<?> mainClass, String[] args)
    {
        Obsidian app = new Obsidian(mainClass, args);
        app.startCli();
        app.init();
        return app;
    }
}