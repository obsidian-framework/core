package com.obsidian.core.storage;

import com.obsidian.core.storage.disks.LocalDisk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StorageManagerTest
{
    @TempDir
    Path tempDir;

    private StorageManager manager;

    @BeforeEach
    void setUp() {
        manager = new StorageManager();
        manager.addDisk("local", new LocalDisk(tempDir.toString(), "http://localhost"));
        manager.setDefault("local");
    }

    // ──────────────────────────────────────────────
    // disk selection
    // ──────────────────────────────────────────────

    @Test
    void disk_default_returnsLocalDisk() {
        assertNotNull(manager.disk());
    }

    @Test
    void disk_byName_returnsCorrectDisk() {
        assertNotNull(manager.disk("local"));
    }

    @Test
    void disk_unknownName_throws() {
        assertThrows(StorageException.class, () -> manager.disk("s3"));
    }

    @Test
    void setDefault_changesDefaultDisk() {
        Path otherDir = tempDir.resolve("other");
        otherDir.toFile().mkdirs();
        manager.addDisk("other", new LocalDisk(otherDir.toString(), "http://other"));
        manager.setDefault("other");

        // Should not throw — uses "other" disk now
        manager.put("test.txt", "hello".getBytes());
        assertTrue(manager.disk("other").exists("test.txt"));
    }

    // ──────────────────────────────────────────────
    // Delegation — put / get / exists / delete / list
    // ──────────────────────────────────────────────

    @Test
    void put_and_get_bytes() {
        manager.put("file.txt", "content".getBytes());

        assertArrayEquals("content".getBytes(), manager.get("file.txt"));
    }

    @Test
    void exists_afterPut_returnsTrue() {
        manager.put("file.txt", "x".getBytes());

        assertTrue(manager.exists("file.txt"));
    }

    @Test
    void exists_noPut_returnsFalse() {
        assertFalse(manager.exists("nope.txt"));
    }

    @Test
    void delete_removesFile() {
        manager.put("file.txt", "x".getBytes());
        manager.delete("file.txt");

        assertFalse(manager.exists("file.txt"));
    }

    @Test
    void list_returnsFiles() {
        manager.put("docs/a.txt", "a".getBytes());
        manager.put("docs/b.txt", "b".getBytes());

        List<String> files = manager.list("docs");
        assertEquals(2, files.size());
    }

    @Test
    void url_delegatesToDisk() {
        String url = manager.url("images/photo.jpg");

        assertEquals("http://localhost/images/photo.jpg", url);
    }

    // ──────────────────────────────────────────────
    // Multiple disks
    // ──────────────────────────────────────────────

    @Test
    void multipleDisk_isolation() {
        Path secondDir = tempDir.resolve("second");
        secondDir.toFile().mkdirs();
        manager.addDisk("backup", new LocalDisk(secondDir.toString(), "http://backup"));

        manager.disk("local").put("local-only.txt", "local".getBytes());
        manager.disk("backup").put("backup-only.txt", "backup".getBytes());

        assertTrue(manager.disk("local").exists("local-only.txt"));
        assertFalse(manager.disk("local").exists("backup-only.txt"));
        assertTrue(manager.disk("backup").exists("backup-only.txt"));
        assertFalse(manager.disk("backup").exists("local-only.txt"));
    }

    // ──────────────────────────────────────────────
    // Chaining
    // ──────────────────────────────────────────────

    @Test
    void addDisk_returnsThis_forChaining() {
        StorageManager result = new StorageManager()
                .addDisk("a", new LocalDisk(tempDir.toString(), "http://a"))
                .setDefault("a");

        assertNotNull(result);
    }
}