package com.obsidian.core.livereload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Watches file system paths for changes and triggers a callback.
 * Uses Java NIO WatchService — zero external dependencies.
 */
public class FileWatcher implements Runnable
{
    private static final Logger logger = LoggerFactory.getLogger(FileWatcher.class);

    private final WatchService watchService;
    private final Runnable onChangeCallback;

    /** Debounce delay in ms to avoid multiple rapid reloads */
    private static final long DEBOUNCE_MS = 1000;

    /**
     * Creates a FileWatcher for the given paths.
     *
     * @param paths            List of directories to watch recursively
     * @param onChangeCallback Callback triggered when a file changes
     */
    public FileWatcher(List<Path> paths, Runnable onChangeCallback) throws IOException
    {
        this.watchService = FileSystems.getDefault().newWatchService();
        this.onChangeCallback = onChangeCallback;

        for (Path path : paths) {
            if (Files.exists(path)) {
                registerRecursive(path);
                logger.info("[LiveReload] Watching: {}", path.toAbsolutePath());
            } else {
                logger.warn("[LiveReload] Path does not exist, skipping: {}", path.toAbsolutePath());
            }
        }
    }

    /**
     * Recursively registers all subdirectories under root.
     */
    private void registerRecursive(Path root) throws IOException
    {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
            {
                dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Starts the watch loop. Blocks until interrupted.
     */
    @Override
    public void run()
    {
        logger.info("[LiveReload] File watcher started.");

        while (!Thread.currentThread().isInterrupted()) {
            try {
                WatchKey key = watchService.take();

                // Debounce: wait a bit to batch rapid successive changes
                Thread.sleep(DEBOUNCE_MS);
                key.pollEvents();

                if (!key.reset()) {
                    logger.warn("[LiveReload] Watch key no longer valid.");
                    break;
                }

                onChangeCallback.run();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("[LiveReload] File watcher interrupted, stopping.");
            }
        }
    }
}
