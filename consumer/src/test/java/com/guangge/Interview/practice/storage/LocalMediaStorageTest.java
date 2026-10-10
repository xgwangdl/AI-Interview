package com.guangge.Interview.practice.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalMediaStorageTest {
    @TempDir
    Path tempDir;

    @Test
    void storesContentAndReplacesExistingMedia() throws IOException {
        LocalMediaStorage storage = new LocalMediaStorage(tempDir.toString());

        storage.store("answer.webm", content("first answer"), 12);
        storage.store("answer.webm", content("updated answer"), 14);

        assertEquals("updated answer", Files.readString(storage.resolve("answer.webm")));
    }

    @Test
    void findsStoredMediaAndReportsMissingObjects() throws IOException {
        LocalMediaStorage storage = new LocalMediaStorage(tempDir.toString());
        byte[] answer = "answer".getBytes(StandardCharsets.UTF_8);
        storage.store("answer.webm", new ByteArrayInputStream(answer), answer.length);

        Resource stored = storage.find("answer.webm").orElseThrow();

        assertEquals("answer", new String(stored.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        assertEquals(answer.length, stored.contentLength());
        assertTrue(storage.find("missing.webm").isEmpty());
    }

    @Test
    void deleteRemovesMediaAndIsSafeWhenRepeated() throws IOException {
        LocalMediaStorage storage = new LocalMediaStorage(tempDir.toString());
        storage.store("answer.webm", content("answer"), 6);
        Path media = storage.resolve("answer.webm");

        storage.delete("answer.webm");
        storage.delete("answer.webm");

        assertFalse(Files.exists(media));
        assertTrue(storage.find("answer.webm").isEmpty());
    }

    @Test
    void rejectsKeysThatEscapeStorageRoot() {
        LocalMediaStorage storage = new LocalMediaStorage(tempDir.toString());

        assertThrows(IllegalArgumentException.class, () -> storage.resolve("../outside.webm"));
        assertThrows(IllegalArgumentException.class,
                () -> storage.store("../outside.webm", content("must not be written"), 20));
    }

    private static ByteArrayInputStream content(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
