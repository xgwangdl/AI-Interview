package com.guangge.Interview.privacy;

import java.time.Instant;

public record RecordingConsentView(
        String consentType,
        String version,
        boolean accepted,
        Instant acceptedAt) {
}
