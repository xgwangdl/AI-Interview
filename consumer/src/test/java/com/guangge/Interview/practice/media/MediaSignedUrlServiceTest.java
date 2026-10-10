package com.guangge.Interview.practice.media;

import com.guangge.Interview.practice.dto.MediaSignedUrlView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaSignedUrlServiceTest {

    private final MediaSignedUrlService service = new MediaSignedUrlService("test-secret", 300);

    @Test
    void issuesAUrlThatVerifies() {
        UUID answerId = UUID.randomUUID();
        MediaSignedUrlView signed = service.issue(answerId);

        assertEquals(300, signed.ttlSeconds());
        assertTrue(signed.url().startsWith("/api/v1/media/" + answerId + "?expires="));
        assertTrue(signed.expiresAt().isAfter(Instant.now()));
        assertDoesNotThrow(() -> service.verify(answerId, signed.expiresAt().getEpochSecond(), signatureOf(signed)));
    }

    @Test
    void rejectsATamperedSignature() {
        UUID answerId = UUID.randomUUID();
        MediaSignedUrlView signed = service.issue(answerId);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.verify(answerId, signed.expiresAt().getEpochSecond(), "not-a-real-signature"));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void rejectsAnExpiredEvenIfCorrectlySignedUrl() {
        UUID answerId = UUID.randomUUID();
        long past = Instant.now().getEpochSecond() - 10;

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.verify(answerId, past, service.signature(answerId, past)));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void rejectsASignatureIssuedForAnotherAnswer() {
        UUID answerId = UUID.randomUUID();
        MediaSignedUrlView signed = service.issue(answerId);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.verify(UUID.randomUUID(), signed.expiresAt().getEpochSecond(), signatureOf(signed)));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void rejectsARequestWithoutSignatureParameters() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.verify(UUID.randomUUID(), null, null));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
    }

    private static String signatureOf(MediaSignedUrlView signed) {
        return signed.url().substring(signed.url().indexOf("signature=") + "signature=".length());
    }
}
