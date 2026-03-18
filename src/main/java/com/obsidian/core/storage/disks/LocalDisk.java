package com.obsidian.core.storage.disks;

import com.obsidian.core.storage.StorageDisk;
import com.obsidian.core.storage.StorageException;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Local filesystem disk implementation.
 * All paths are resolved relative to a configured root directory.
 */
public class LocalDisk implements StorageDisk
{
    private final Path root;
    private final String baseUrl;

    /**
     * Creates a new LocalDisk.
     *
     * @param root    The absolute or relative root directory on the filesystem
     * @param baseUrl The base URL used to generate public file URLs
     */
    public LocalDisk(String root, String baseUrl) {
        this.root = Path.of(root).toAbsolutePath();
        this.baseUrl = baseUrl.replaceAll("/$", "");
    }

    /**
     * Writes raw bytes to the given path, creating parent directories as needed.
     *
     * @param path    The destination path relative to the disk root
     * @param content The bytes to write
     */
    @Override
    public void put(String path, byte[] content) {
        try {
            Path target = resolve(path);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new StorageException("Cannot write: " + path, e);
        }
    }

    /**
     * Writes a stream to the given path, creating parent directories as needed.
     *
     * @param path   The destination path relative to the disk root
     * @param stream The input stream to read from
     */
    @Override
    public void put(String path, InputStream stream) {
        try {
            Path target = resolve(path);
            Files.createDirectories(target.getParent());
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("Cannot write stream: " + path, e);
        }
    }

    /**
     * Reads the file at the given path as a byte array.
     *
     * @param path The file path relative to the disk root
     * @return The raw file content
     */
    @Override
    public byte[] get(String path) {
        try {
            return Files.readAllBytes(resolve(path));
        } catch (IOException e) {
            throw new StorageException("Cannot read: " + path, e);
        }
    }

    /**
     * Opens a stream to the file at the given path.
     *
     * @param path The file path relative to the disk root
     * @return An open InputStream for the file
     */
    @Override
    public InputStream stream(String path) {
        try {
            return Files.newInputStream(resolve(path));
        } catch (IOException e) {
            throw new StorageException("Cannot open stream: " + path, e);
        }
    }

    /**
     * Deletes the file at the given path if it exists.
     *
     * @param path The file path relative to the disk root
     */
    @Override
    public void delete(String path) {
        try {
            Files.deleteIfExists(resolve(path));
        } catch (IOException e) {
            throw new StorageException("Cannot delete: " + path, e);
        }
    }

    /**
     * Checks whether a file exists at the given path.
     *
     * @param path The file path relative to the disk root
     * @return True if the file exists, false otherwise
     */
    @Override
    public boolean exists(String path) {
        return Files.exists(resolve(path));
    }

    /**
     * Lists all files in the given directory.
     *
     * @param directory The directory path relative to the disk root
     * @return A list of file paths relative to the disk root, or an empty list if the directory does not exist
     */
    @Override
    public List<String> list(String directory) {
        try {
            Path dir = resolve(directory);
            if (!Files.isDirectory(dir)) return List.of();
            return Files.list(dir)
                    .map(p -> root.relativize(p).toString())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new StorageException("Cannot list: " + directory, e);
        }
    }

    /**
     * Returns the public URL for the given file path.
     *
     * @param path The file path relative to the disk root
     * @return The absolute public URL
     */
    @Override
    public String url(String path) {
        return baseUrl + "/" + path.replaceAll("^/", "");
    }

    /**
     * Resolves a relative path against the disk root, rejecting path traversal attempts.
     *
     * @param path The relative path to resolve
     * @return The resolved absolute Path
     */
    private Path resolve(String path) {
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root)) {
            throw new StorageException("Path traversal detected: " + path, null);
        }
        return resolved;
    }
}