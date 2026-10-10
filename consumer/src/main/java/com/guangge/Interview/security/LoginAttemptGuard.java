package com.guangge.Interview.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录尝试限流。
 *
 * 邀请码是唯一凭证，且通常是短数字串。没有限流就可以离线爆破，
 * 一旦命中即拿到该候选人的录像、转写与简历相关数据。
 * 这里按用户名统计失败次数（成功即清零），超过阈值后在一段时间内直接拒绝。
 */
@Component
public class LoginAttemptGuard {

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptGuard(
            @Value("${auth.login.max-attempts:10}") int maxAttempts,
            @Value("${auth.login.lock-window-minutes:15}") long lockWindowMinutes) {
        this(maxAttempts, Duration.ofMinutes(lockWindowMinutes), Clock.systemUTC());
    }

    LoginAttemptGuard(int maxAttempts, Duration window, Clock clock) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.window = window;
        this.clock = clock;
    }

    /** 用户名做归一化，避免大小写或空格绕过计数。 */
    public String keyOf(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isLocked(String key) {
        Deque<Instant> attempts = failures.get(key);
        if (attempts == null) return false;
        synchronized (attempts) {
            purge(attempts);
            return attempts.size() >= maxAttempts;
        }
    }

    public void recordFailure(String key) {
        Deque<Instant> attempts = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            purge(attempts);
            attempts.addLast(clock.instant());
        }
    }

    public void clear(String key) {
        failures.remove(key);
    }

    private void purge(Deque<Instant> attempts) {
        Instant cutoff = clock.instant().minus(window);
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
            attempts.pollFirst();
        }
    }
}
