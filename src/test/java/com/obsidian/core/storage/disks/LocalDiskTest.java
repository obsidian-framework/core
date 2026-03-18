package com.obsidian.core.storage.disks;

import com.obsidian.core.storage.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocalDiskTest
{
    @TempDir
    Path tempDir;

    private LocalDisk disk;

    @BeforeEach
    void setUp() {
        disk = new LocalDisk(tempDir.toString(), "http://localhost:8080/storage");
    }

    // ──────────────────────────────────────────────
    // put (bytes) / get
    // ──────────────────────────────────────────────

    @Test
    void put_bytes_and_get() {
        disk.put("file.txt", "hello".getBytes());

        assertArrayEquals("hello".getBytes(), disk.get("file.txt"));
    }

    @Test
    void put_bytes_createsParentDirectories() {
        disk.put("a/b/c/deep.txt", "deep".getBytes());

        assertTrue(disk.exists("a/b/c/deep.txt"));
        assertArrayEquals("deep".getBytes(), disk.get("a/b/c/deep.txt"));
    }

    @Test
    void put_bytes_overwritesExisting() {
        disk.put("file.txt", "old".getBytes());
        disk.put("file.txt", "new".getBytes());

        assertArrayEquals("new".getBytes(), disk.get("file.txt"));
    }

    // ──────────────────────────────────────────────
    // put (stream)
    // ──────────────────────────────────────────────

    @Test
    void put_stream_writesContent() {
        InputStream stream = new ByteArrayInputStream("streamed".getBytes());
        disk.put("stream.txt", stream);

        assertArrayEquals("streamed".getBytes(), disk.get("stream.txt"));
    }

    // ──────────────────────────────────────────────
    // get — errors
    // ──────────────────────────────────────────────

    @Test
    void get_nonexistent_throws() {
        assertThrows(StorageException.class, () -> disk.get("nope.txt"));
    }

    // ──────────────────────────────────────────────
    // stream
    // ──────────────────────────────────────────────

    @Test
    void stream_returnsReadableStream() throws Exception {
        disk.put("file.txt", "content".getBytes());

        try (InputStream in = disk.stream("file.txt")) {
            String result = new String(in.readAllBytes());
            assertEquals("content", result);
        }
    }

    // ──────────────────────────────────────────────
    // exists
    // ──────────────────────────────────────────────

    @Test
    void exists_existingFile_returnsTrue() {
        disk.put("file.txt", "x".getBytes());

        assertTrue(disk.exists("file.txt"));
    }

    @Test
    void exists_missingFile_returnsFalse() {
        assertFalse(disk.exists("nope.txt"));
    }

    // ──────────────────────────────────────────────
    // delete
    // ──────────────────────────────────────────────

    @Test
    void delete_removesFile() {
        disk.put("file.txt", "x".getBytes());
        disk.delete("file.txt");

        assertFalse(disk.exists("file.txt"));
    }

    @Test
    void delete_nonexistent_doesNotThrow() {
        assertDoesNotThrow(() -> disk.delete("nope.txt"));
    }

    // ──────────────────────────────────────────────
    // list
    // ──────────────────────────────────────────────

    @Test
    void list_returnsFilesInDirectory() {
        disk.put("docs/a.txt", "a".getBytes());
        disk.put("docs/b.txt", "b".getBytes());

        List<String> files = disk.list("docs");

        assertEquals(2, files.size());
        assertTrue(files.stream().anyMatch(f -> f.contains("a.txt")));
        assertTrue(files.stream().anyMatch(f -> f.contains("b.txt")));
    }

    @Test
    void list_nonexistentDirectory_returnsEmpty() {
        List<String> files = disk.list("nonexistent");

        assertTrue(files.isEmpty());
    }

    @Test
    void list_emptyDirectory() throws Exception {
        Files.createDirectories(tempDir.resolve("empty"));

        List<String> files = disk.list("empty");

        assertTrue(files.isEmpty());
    }

    // ──────────────────────────────────────────────
    // url
    // ──────────────────────────────────────────────

    @Test
    void url_generatesCorrectUrl() {
        assertEquals("http://localhost:8080/storage/images/photo.jpg", disk.url("images/photo.jpg"));
    }

    @Test
    void url_stripsLeadingSlash() {
        assertEquals("http://localhost:8080/storage/file.txt", disk.url("/file.txt"));
    }

    @Test
    void url_baseUrlTrailingSlash() {
        LocalDisk d = new LocalDisk(tempDir.toString(), "http://localhost/storage/");

        assertEquals("http://localhost/storage/file.txt", d.url("file.txt"));
    }

    // ──────────────────────────────────────────────
    // Path traversal protection
    // ──────────────────────────────────────────────

    @Test
    void resolve_pathTraversal_throws() {
        assertThrows(StorageException.class, () -> disk.put("../../etc/passwd", "hacked".getBytes()));
    }

    @Test
    void resolve_pathTraversal_get_throws() {
        assertThrows(StorageException.class, () -> disk.get("../../../etc/passwd"));
    }
}