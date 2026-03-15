package fr.kainovaii.obsidian.storage;

import fr.kainovaii.obsidian.core.EnvLoader;
import fr.kainovaii.obsidian.di.Container;
import fr.kainovaii.obsidian.storage.disks.LocalDisk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initializes and registers the storage subsystem into the DI container.
 * Reads disk configuration from environment variables and binds a {@link StorageManager} singleton.
 */
public class StorageLoader
{
    private static final Logger logger = LoggerFactory.getLogger(StorageLoader.class);

    /**
     * Loads storage configuration and registers the {@link StorageManager} singleton.
     */
    public static void loadStorage() {
        EnvLoader env = EnvLoader.getInstance();

        String localRoot = env.get("STORAGE_LOCAL_ROOT", "storage/app");
        String localUrl  = env.get("STORAGE_LOCAL_URL",  "http://localhost:4567/storage");
        String defaultDisk = env.get("STORAGE_DISK", "local");

        StorageManager manager = new StorageManager()
                .addDisk("local",  new LocalDisk(localRoot, localUrl))
                .addDisk("public", new LocalDisk(localRoot + "/public", localUrl + "/public"))
                .setDefault(defaultDisk);

        Container.singleton(StorageManager.class, manager);
        logger.debug("Storage loaded (default disk: {})", defaultDisk);
    }
}