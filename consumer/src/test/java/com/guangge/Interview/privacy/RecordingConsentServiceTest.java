package com.guangge.Interview.privacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordingConsentServiceTest {

    private static final String OWNER = "alice";
    private static final String VERSION = "recording-2026-11-17";

    private final RecordingConsentRepository consents = mock(RecordingConsentRepository.class);
    private RecordingConsentService service;

    @BeforeEach
    void setUp() {
        service = new RecordingConsentService(consents, VERSION);
    }

    @Test
    void reportsMissingConsentForTheCurrentVersion() {
        RecordingConsentView status = service.status(OWNER);

        assertFalse(status.accepted());
        assertEquals(VERSION, status.version());
    }

    @Test
    void recordsConsentOnceAndReturnsTheOriginalAcceptance() {
        AtomicReference<RecordingConsentEntity> stored = new AtomicReference<>();
        when(consents.findFirstByOwnerIdAndConsentTypeAndVersionOrderByAcceptedAtDesc(
                OWNER, RecordingConsentService.TYPE_RECORDING, VERSION))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(consents.save(any(RecordingConsentEntity.class))).thenAnswer(invocation -> {
            RecordingConsentEntity saved = invocation.getArgument(0);
            stored.set(consent(saved.getAcceptedAt()));
            return stored.get();
        });

        RecordingConsentView first = service.accept(OWNER, VERSION);
        RecordingConsentView second = service.accept(OWNER, VERSION);

        assertTrue(first.accepted());
        assertEquals(first.acceptedAt(), second.acceptedAt());
        verify(consents).save(any(RecordingConsentEntity.class));
    }

    @Test
    void rejectsAStaleConsentVersionInsteadOfSilentlyRecordingIt() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.accept(OWNER, "recording-old-version"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(consents, never()).save(any());
    }

    @Test
    void requireRecordingConsentReturns428BeforeAnyMediaCanBeStored() {
        when(consents.findFirstByOwnerIdAndConsentTypeAndVersionOrderByAcceptedAtDesc(
                OWNER, RecordingConsentService.TYPE_RECORDING, VERSION)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.requireRecordingConsent(OWNER));

        assertEquals(HttpStatus.PRECONDITION_REQUIRED, error.getStatusCode());
    }

    private static RecordingConsentEntity consent(Instant acceptedAt) {
        RecordingConsentEntity entity = new RecordingConsentEntity();
        entity.setOwnerId(OWNER);
        entity.setConsentType(RecordingConsentService.TYPE_RECORDING);
        entity.setVersion(VERSION);
        entity.setAcceptedAt(acceptedAt);
        return entity;
    }
}
