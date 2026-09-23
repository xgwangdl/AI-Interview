package com.guangge.Interview.practice.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalMediaStorageTest {
    @TempDir
    Path tempDir;

    @Test
    void storesContentAndReplacesExistingMedia() throws IOException {
        LocalMediaStorage storage = new LocalMediaStorage(tempDir.toString());

        storage.store("answer.webm", content("first answer"));
        storage.store("answer.webm", content("updated answer"));

        assertEquals("updated answer", Files.readString(storage.resolve("answer.webm")));
    }

    @Test
    void deleteRemovesMediaAndIsSafeWhenRepeated() throws IOException {
        LocalMediaStorage storage = new LocalMediaStorage(tempDir.toString());
        storage.store("answer.webm", content("answer"));
        Path media = storage.resolve("answer.webm");

        storage.delete("answer.webm");
        storage.delete("answer.webm");

        assertFalse(Files.exists(media));
    }

    @Test
    void rejectsKeysThatEscapeStorageRoot() {
        LocalMediaStorage storage = new LocalMediaStorage(tempDir.toString());

        assertThrows(IllegalArgumentException.class, () -> storage.resolve("../outside.webm"));
        assertThrows(IllegalArgumentException.class,
                () -> storage.store("../outside.webm", content("must not be written")));
    }

    private static ByteArrayInputStream content(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
