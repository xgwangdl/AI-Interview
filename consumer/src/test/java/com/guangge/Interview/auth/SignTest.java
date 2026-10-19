package com.guangge.Interview.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SignTest {

    @BeforeEach
    void clearCaches() throws Exception {
        cache("VERIFIERS").clear();
        cache("ALGORITHMS").clear();
    }

    @Test
    void cachesOneVerifierPerSecretInsteadOfOnePerToken() throws Exception {
        String secret = "cache-test-secret";
        for (int index = 0; index < 100; index++) {
            String token = Sign.getToken("user-" + index, secret);
            assertEquals("user-" + index, Sign.verifyToken(token, secret).getClaim("userName").asString());
        }

        Map<?, ?> verifiers = cache("VERIFIERS");
        assertEquals(1, verifiers.size());
    }

    @Test
    void concurrentVerificationIsStable() throws Exception {
        String secret = "concurrent-test-secret";
        List<String> tokens = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            tokens.add(Sign.getToken("user-" + index, secret));
        }

        ExecutorService executor = Executors.newFixedThreadPool(16);
        try {
            List<Callable<String>> jobs = new ArrayList<>();
            for (String token : tokens) {
                for (int index = 0; index < 100; index++) {
                    jobs.add(() -> Sign.verifyToken(token, secret).getClaim("userName").asString());
                }
            }
            List<Future<String>> results = executor.invokeAll(jobs);
            for (Future<String> result : results) {
                result.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, cache("VERIFIERS").size());
    }

    private static Map<?, ?> cache(String fieldName) throws Exception {
        Field field = Sign.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Map<?, ?>) field.get(null);
    }
}
