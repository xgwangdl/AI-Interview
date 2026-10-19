package com.guangge.Interview.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingWindowRateLimiterTest {

    @Test
    void blocksTheRequestAfterTheLimitAndRecoversAfterTheWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-19T00:00:00Z"));
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(2, Duration.ofMinutes(1), clock);

        assertTrue(limiter.allow("127.0.0.1"));
        assertTrue(limiter.allow("127.0.0.1"));
        assertFalse(limiter.allow("127.0.0.1"));

        clock.advance(Duration.ofSeconds(61));
        assertTrue(limiter.allow("127.0.0.1"));
    }

    @Test
    void keepsDifferentClientsIndependent() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(
                1, Duration.ofHours(1), Clock.fixed(Instant.now(), ZoneId.systemDefault()));

        assertTrue(limiter.allow("a"));
        assertFalse(limiter.allow("a"));
        assertTrue(limiter.allow("b"));
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
