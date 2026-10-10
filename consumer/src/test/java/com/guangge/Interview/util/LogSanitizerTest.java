package com.guangge.Interview.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSanitizerTest {

    @Test
    void redactsBearerTokens() {
        String sanitized = LogSanitizer.sanitize("Authorization: Bearer sk-abc123456789");

        assertFalse(sanitized.contains("sk-abc123456789"), sanitized);
        assertTrue(sanitized.contains(LogSanitizer.REDACTED), sanitized);
    }

    @Test
    void redactsKeyValueSecrets() {
        String sanitized = LogSanitizer.sanitize("api-key=secret-value password: hunter2");

        assertFalse(sanitized.contains("secret-value"), sanitized);
        assertFalse(sanitized.contains("hunter2"), sanitized);
    }

    @Test
    void redactsContactDetails() {
        String sanitized = LogSanitizer.sanitize("候选人 candidate@example.com 电话 13800138000");

        assertFalse(sanitized.contains("candidate@example.com"), sanitized);
        assertFalse(sanitized.contains("13800138000"), sanitized);
        assertTrue(sanitized.contains("候选人"), sanitized);
    }

    @Test
    void truncatesLongValues() {
        String longValue = "答".repeat(500);

        String sanitized = LogSanitizer.sanitize(longValue);

        assertTrue(sanitized.startsWith("答".repeat(LogSanitizer.MAX_LENGTH)), sanitized);
        assertTrue(sanitized.contains("300 more chars"), sanitized);
        assertTrue(sanitized.length() < longValue.length(), sanitized);
    }

    @Test
    void keepsShortHarmlessValuesIntact() {
        assertEquals("asr provider is down", LogSanitizer.sanitize("asr provider is down"));
    }

    @Test
    void handlesNullAndBlank() {
        assertEquals("", LogSanitizer.sanitize(null));
        assertEquals("", LogSanitizer.sanitize("   "));
        assertEquals("", LogSanitizer.truncate(null, 10));
        assertEquals("", LogSanitizer.truncate("value", 0));
    }
}
