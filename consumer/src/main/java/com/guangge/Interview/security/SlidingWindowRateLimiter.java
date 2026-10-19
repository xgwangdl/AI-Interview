package com.guangge.Interview.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 进程内滑动窗口限流；用于注册/登录的边缘保护，不替代生产网关限流。 */
public class SlidingWindowRateLimiter {

    private static final int MAX_TRACKED_KEYS = 20_000;

    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> requests = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(int limit, Duration window, Clock clock) {
        this.limit = Math.max(1, limit);
        this.window = window;
        this.clock = clock;
    }

    public boolean allow(String key) {
        if (requests.size() > MAX_TRACKED_KEYS) cleanup();
        Deque<Instant> attempts = requests.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            purge(attempts);
            if (attempts.size() >= limit) return false;
            attempts.addLast(clock.instant());
            return true;
        }
    }

    private void cleanup() {
        requests.forEach((key, attempts) -> {
            synchronized (attempts) {
                purge(attempts);
                if (attempts.isEmpty()) requests.remove(key, attempts);
            }
        });
    }

    private void purge(Deque<Instant> attempts) {
        Instant cutoff = clock.instant().minus(window);
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
            attempts.pollFirst();
        }
    }
}
