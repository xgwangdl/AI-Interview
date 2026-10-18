package com.guangge.Interview.practice.media;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaSignatureValidatorTest {

    @Test
    void acceptsWebmAndPreservesTheFullStream() throws Exception {
        byte[] content = video(512);

        try (var validated = MediaSignatureValidator.requireSupportedVideo(
                new ByteArrayInputStream(content), content.length)) {
            assertArrayEquals(content, validated.readAllBytes());
        }
    }

    @Test
    void acceptsMp4FtypBox() throws Exception {
        byte[] content = new byte[512];
        content[4] = 'f';
        content[5] = 't';
        content[6] = 'y';
        content[7] = 'p';

        try (var validated = MediaSignatureValidator.requireSupportedVideo(
                new ByteArrayInputStream(content), content.length)) {
            assertEquals(512, validated.readAllBytes().length);
        }
    }

    @Test
    void rejectsTheSeventeenBytePlaceholderBeforeAnalysis() {
        byte[] placeholder = "not-a-real-video".getBytes(StandardCharsets.UTF_8);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> MediaSignatureValidator.requireSupportedVideo(
                        new ByteArrayInputStream(placeholder), placeholder.length));

        assertEquals(400, exception.getStatusCode().value());
        assertEquals("录制文件过小或不完整，请重新录制后再上传", exception.getReason());
    }

    @Test
    void rejectsUnknownContentEvenWhenItIsLargeEnough() {
        byte[] content = new byte[1024];
        Arrays.fill(content, (byte) 'A');

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> MediaSignatureValidator.requireSupportedVideo(
                        new ByteArrayInputStream(content), content.length));

        assertEquals(415, exception.getStatusCode().value());
    }

    private static byte[] video(int size) {
        byte[] content = new byte[size];
        Arrays.fill(content, (byte) 0x42);
        content[0] = 0x1a;
        content[1] = 0x45;
        content[2] = (byte) 0xdf;
        content[3] = (byte) 0xa3;
        return content;
    }
}
