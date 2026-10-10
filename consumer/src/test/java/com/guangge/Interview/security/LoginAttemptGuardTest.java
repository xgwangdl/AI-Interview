package com.guangge.Interview.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptGuardTest {

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptGuard guard = new LoginAttemptGuard(3, Duration.ofMinutes(15), clock);

    @Test
    void locksAfterRepeatedFailuresForTheSameName() {
        for (int attempt = 0; attempt < 3; attempt++) {
            guard.recordFailure("alice");
        }

        assertTrue(guard.isLocked("alice"));
        assertFalse(guard.isLocked("bob"), "不应牵连其他用户名");
    }

    @Test
    void aSuccessfulLoginClearsTheCounter() {
        guard.recordFailure("alice");
        guard.recordFailure("alice");

        guard.clear("alice");

        assertFalse(guard.isLocked("alice"));
    }

    @Test
    void failuresExpireAfterTheWindow() {
        for (int attempt = 0; attempt < 3; attempt++) {
            guard.recordFailure("alice");
        }
        assertTrue(guard.isLocked("alice"));

        clock.advance(Duration.ofMinutes(16));

        assertFalse(guard.isLocked("alice"));
    }

    @Test
    void normalisesTheUserNameSoCaseAndSpacesCannotBypassTheLimit() {
        assertEquals("alice", guard.keyOf("  Alice "));
        assertEquals("", guard.keyOf(null));
    }

    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
