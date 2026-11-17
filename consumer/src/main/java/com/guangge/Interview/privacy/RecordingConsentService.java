package com.guangge.Interview.privacy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
public class RecordingConsentService {

    public static final String TYPE_RECORDING = "recording";

    private final RecordingConsentRepository consents;
    private final String currentVersion;

    public RecordingConsentService(
            RecordingConsentRepository consents,
            @Value("${privacy.recording-consent.version:2026-11-17-v1}") String currentVersion) {
        this.consents = consents;
        this.currentVersion = requireText(currentVersion, "recording consent version");
    }

    @Transactional(readOnly = true)
    public RecordingConsentView status(String ownerId) {
        return consents
                .findFirstByOwnerIdAndConsentTypeAndVersionOrderByAcceptedAtDesc(
                        ownerId, TYPE_RECORDING, currentVersion)
                .map(entity -> new RecordingConsentView(
                        TYPE_RECORDING, currentVersion, true, entity.getAcceptedAt()))
                .orElseGet(() -> new RecordingConsentView(TYPE_RECORDING, currentVersion, false, null));
    }

    @Transactional
    public RecordingConsentView accept(String ownerId, String version) {
        if (!currentVersion.equals(version)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "隐私说明已更新，请刷新页面后重新确认");
        }
        RecordingConsentEntity entity = consents
                .findFirstByOwnerIdAndConsentTypeAndVersionOrderByAcceptedAtDesc(
                        ownerId, TYPE_RECORDING, currentVersion)
                .orElseGet(() -> {
                    RecordingConsentEntity created = new RecordingConsentEntity();
                    created.setOwnerId(ownerId);
                    created.setConsentType(TYPE_RECORDING);
                    created.setVersion(currentVersion);
                    created.setAcceptedAt(Instant.now());
                    return consents.save(created);
                });
        return new RecordingConsentView(TYPE_RECORDING, currentVersion, true, entity.getAcceptedAt());
    }

    @Transactional(readOnly = true)
    public void requireRecordingConsent(String ownerId) {
        if (!status(ownerId).accepted()) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "RECORDING_CONSENT_REQUIRED");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.strip();
    }
}
