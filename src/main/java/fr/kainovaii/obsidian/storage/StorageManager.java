package fr.kainovaii.obsidian.storage;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central facade for managing named storage disks.
 * Registered at boot via {@link StorageLoader} and injectable through the DI container.
 */
public class StorageManager
{
    private final Map<String, StorageDisk> disks = new HashMap<>();
    private String defaultDisk = "local";

    /**
     * Registers a disk under the given name.
     *
     * @param name The disk identifier
     * @param disk The disk implementation to register
     * @return This instance for chaining
     */
    public StorageManager addDisk(String name, StorageDisk disk) {
        disks.put(name, disk);
        return this;
    }

    /**
     * Sets the default disk name used when no disk is specified.
     *
     * @param name The disk identifier to use as default
     * @return This instance for chaining
     */
    public StorageManager setDefault(String name) {
        this.defaultDisk = name;
        return this;
    }

    /**
     * Retrieves a disk by name.
     *
     * @param name The disk identifier
     * @return The registered disk
     */
    public StorageDisk disk(String name) {
        StorageDisk disk = disks.get(name);
        if (disk == null) throw new StorageException("Unknown disk: " + name, null);
        return disk;
    }

    /**
     * Retrieves the default disk.
     *
     * @return The default disk
     */
    public StorageDisk disk() {
        return disk(defaultDisk);
    }

    /**
     * Writes raw bytes to the given path on the default disk.
     *
     * @param path    The destination path relative to the disk root
     * @param content The bytes to write
     */
    public void put(String path, byte[] content) {
        disk().put(path, content);
    }

    /**
     * Writes a stream to the given path on the default disk.
     *
     * @param path   The destination path relative to the disk root
     * @param stream The input stream to read from
     */
    public void put(String path, InputStream stream) {
        disk().put(path, stream);
    }

    /**
     * Stores an uploaded file in the given directory on the default disk.
     * The filename is generated automatically using a UUID.
     *
     * @param directory The target directory relative to the disk root
     * @param file      The uploaded file to store
     */
    public void putFile(String directory, MultipartFile file) {
        String filename = java.util.UUID.randomUUID() + "." + file.getExtension();
        disk().put(directory + "/" + filename, file.getInputStream());
    }

    /**
     * Reads the file at the given path from the default disk as a byte array.
     *
     * @param path The file path relative to the disk root
     * @return The raw file content
     */
    public byte[] get(String path) {
        return disk().get(path);
    }

    /**
     * Opens a stream to the file at the given path on the default disk.
     *
     * @param path The file path relative to the disk root
     * @return An open InputStream for the file
     */
    public InputStream stream(String path) {
        return disk().stream(path);
    }

    /**
     * Deletes the file at the given path on the default disk.
     *
     * @param path The file path relative to the disk root
     */
    public void delete(String path) {
        disk().delete(path);
    }

    /**
     * Checks whether a file exists at the given path on the default disk.
     *
     * @param path The file path relative to the disk root
     * @return True if the file exists, false otherwise
     */
    public boolean exists(String path) {
        return disk().exists(path);
    }

    /**
     * Lists all files in the given directory on the default disk.
     *
     * @param directory The directory path relative to the disk root
     * @return A list of file paths, or an empty list if the directory does not exist
     */
    public List<String> list(String directory) {
        return disk().list(directory);
    }

    /**
     * Returns the public URL for the given file path on the default disk.
     *
     * @param path The file path relative to the disk root
     * @return The absolute public URL
     */
    public String url(String path) {
        return disk().url(path);
    }
}