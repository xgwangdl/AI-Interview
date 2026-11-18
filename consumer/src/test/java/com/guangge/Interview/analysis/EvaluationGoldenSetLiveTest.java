package com.guangge.Interview.analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Manual quality gate for real providers:
 *
 * mvn -B -pl consumer -am -Dtest=EvaluationGoldenSetLiveTest
 *     -Dai.quality.live=true -Dsurefire.failIfNoSpecifiedTests=false test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfSystemProperty(named = "ai.quality.live", matches = "true")
class EvaluationGoldenSetLiveTest {

    @Autowired
    private EvaluationProvider provider;

    @Autowired
    private ObjectMapper objectMapper;

    @TestFactory
    Stream<DynamicTest> providerStaysInsideHumanReviewedScoreBands() throws Exception {
        List<GoldenCase> cases;
        try (InputStream input = new ClassPathResource("evaluation/golden-cases.json").getInputStream()) {
            cases = objectMapper.readValue(input, new TypeReference<>() { });
        }
        String onlyCase = System.getProperty("ai.quality.case", "").trim();
        return cases.stream()
                .filter(testCase -> onlyCase.isEmpty() || onlyCase.equals(testCase.id()))
                .map(testCase -> DynamicTest.dynamicTest(testCase.id(), () -> assertCase(testCase)));
    }

    private void assertCase(GoldenCase testCase) throws Exception {
        EvaluationOutcome outcome = evaluateWithTimeout(testCase);
        double score = outcome.result().overallScore();
        assertTrue(score >= testCase.expectedMin() && score <= testCase.expectedMax(),
                () -> testCase.id() + " scored " + score + ", expected "
                        + testCase.expectedMin() + "-" + testCase.expectedMax()
                        + ". Rationale: " + testCase.rationale());
        assertFalse(outcome.result().dimensionFeedback().isEmpty(),
                () -> testCase.id() + " must include dimension feedback");
        assertFalse(outcome.result().improvementSuggestions().isEmpty(),
                () -> testCase.id() + " must include actionable suggestions");
    }

    private EvaluationOutcome evaluateWithTimeout(GoldenCase testCase) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "golden-evaluation-" + testCase.id());
            thread.setDaemon(true);
            return thread;
        });
        Future<EvaluationOutcome> future = executor.submit(() -> {
            try {
                return provider.evaluate(new EvaluationRequest(
                        testCase.role(),
                        testCase.jobDescription(),
                        testCase.id(),
                        testCase.question(),
                        testCase.category(),
                        testCase.interviewType(),
                        testCase.transcript(),
                        testCase.durationSeconds()));
            } catch (RuntimeException exception) {
                throw new AssertionError(
                        testCase.id() + " failed quality validation: " + exception.getMessage(), exception);
            }
        });
        try {
            int timeoutSeconds = Integer.getInteger("ai.quality.timeout-seconds", 120);
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new AssertionError(testCase.id() + " provider call timed out", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof AssertionError assertionError) throw assertionError;
            throw new AssertionError(testCase.id() + " provider call failed", cause);
        } finally {
            executor.shutdownNow();
        }
    }

    record GoldenCase(
            String id,
            String role,
            String jobDescription,
            String question,
            String transcript,
            Integer durationSeconds,
            double expectedMin,
            double expectedMax,
            String rationale,
            String category,
            String interviewType) {

        GoldenCase {
            category = category == null ? "technical" : category;
            interviewType = interviewType == null ? "technical" : interviewType;
        }
    }
}
