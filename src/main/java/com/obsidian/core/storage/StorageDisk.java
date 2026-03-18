package com.obsidian.core.storage;

import java.io.InputStream;
import java.util.List;

/**
 * Contract for storage disk implementations.
 * Each disk represents an isolated storage backend (local, S3, etc.).
 */
public interface StorageDisk
{
    /**
     * Writes raw bytes to the given path.
     *
     * @param path    The destination path relative to the disk root
     * @param content The bytes to write
     */
    void put(String path, byte[] content);

    /**
     * Writes a stream to the given path.
     *
     * @param path   The destination path relative to the disk root
     * @param stream The input stream to read from
     */
    void put(String path, InputStream stream);

    /**
     * Reads the file at the given path as a byte array.
     *
     * @param path The file path relative to the disk root
     * @return The raw file content
     */
    byte[] get(String path);

    /**
     * Opens a stream to the file at the given path.
     *
     * @param path The file path relative to the disk root
     * @return An open InputStream for the file
     */
    InputStream stream(String path);

    /**
     * Deletes the file at the given path if it exists.
     *
     * @param path The file path relative to the disk root
     */
    void delete(String path);

    /**
     * Checks whether a file exists at the given path.
     *
     * @param path The file path relative to the disk root
     * @return True if the file exists, false otherwise
     */
    boolean exists(String path);

    /**
     * Lists all files in the given directory.
     *
     * @param directory The directory path relative to the disk root
     * @return A list of file paths, or an empty list if the directory does not exist
     */
    List<String> list(String directory);

    /**
     * Returns the public URL for the given file path.
     *
     * @param path The file path relative to the disk root
     * @return The absolute public URL
     */
    String url(String path);
}