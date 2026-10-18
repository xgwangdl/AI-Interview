package com.guangge.Interview.audio.services;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioConverterTest {

    @Test
    void defaultsToThePathExecutableName() {
        AudioConverter converter = new AudioConverter("", 60);

        assertTrue(converter.executable().equals("ffmpeg"));
    }

    @Test
    void missingExecutableProducesAnActionableError() throws Exception {
        AudioConverter converter = new AudioConverter(
                "C:\\definitely-missing\\ffmpeg-for-light-interview-test.exe", 1);
        File input = File.createTempFile("light-interview-input-", ".webm");
        input.deleteOnExit();

        IOException exception = assertThrows(IOException.class, () -> converter.convertToWav(input));

        assertTrue(exception.getMessage().contains("FFmpeg 不可用"));
        assertTrue(exception.getMessage().contains("FFMPEG_PATH"));
        assertFalse(converter.isAvailable());
    }
}
