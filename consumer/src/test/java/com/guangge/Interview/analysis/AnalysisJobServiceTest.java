package com.guangge.Interview.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.practice.entity.PracticeAnswerEntity;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.repository.PracticeAnswerRepository;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import com.guangge.Interview.practice.service.PracticeQuestionCatalog;
import com.guangge.Interview.practice.storage.MediaStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisJobServiceTest {

    private static final String RAW_RESPONSE = "{\"overallScore\": 82.5, \"note\": \"raw-model-output\"}";

    private AnalysisJobRepository jobs;
    private PracticeSessionRepository sessions;
    private PracticeAnswerRepository answers;
    private MediaStorage mediaStorage;
    private EvaluationResultValidator validator;
    private ObjectMapper objectMapper;

    private UUID sessionId;
    private UUID answerId;
    private PracticeAnswerEntity answer;
    private PracticeSessionEntity session;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        jobs = mock(AnalysisJobRepository.class);
        sessions = mock(PracticeSessionRepository.class);
        answers = mock(PracticeAnswerRepository.class);
        mediaStorage = mock(MediaStorage.class);
        validator = new EvaluationResultValidator(objectMapper);

        sessionId = UUID.randomUUID();
        answerId = UUID.randomUUID();

        session = new PracticeSessionEntity();
        session.setId(sessionId);
        session.setRole("Java 后端");
        session.setJobDescription("负责订单系统的设计与实现");
        session.setQuestionsJson(
                "[{\"id\":\"q-1\",\"text\":\"讲一个你主导的重构\",\"category\":\"design\"}]");

        answer = new PracticeAnswerEntity();
        answer.setId(answerId);
        answer.setSessionId(sessionId);
        answer.setQuestionIndex(0);
        answer.setQuestionId("q-1");
        answer.setMediaKey("media-key");
        answer.setMediaContentType("video/webm");
        answer.setDurationSeconds(120);
        answer.setTranscriptStatus("awaiting_provider");
        answer.setEvaluationStatus("awaiting_provider");
    }

    @Test
    void queuesOneJobPerRecordedAnswer() {
        when(answers.findAllBySessionIdOrderByQuestionIndexAsc(sessionId)).thenReturn(List.of(answer));
        when(answers.findById(answerId)).thenReturn(Optional.of(answer));
        when(jobs.findByAnswerId(answerId)).thenReturn(Optional.empty());

        int created = service(stubAsr("我主导了订单系统的重构"), stubEvaluation(), 3).enqueue(sessionId);

        assertEquals(1, created);
        verify(jobs).save(any(AnalysisJobEntity.class));
    }

    @Test
    void skipsAnswersThatAreAlreadyQueued() {
        when(answers.findAllBySessionIdOrderByQuestionIndexAsc(sessionId)).thenReturn(List.of(answer));
        when(answers.findById(answerId)).thenReturn(Optional.of(answer));
        when(jobs.findByAnswerId(answerId)).thenReturn(Optional.of(pendingJob()));

        int created = service(stubAsr("..."), stubEvaluation(), 3).enqueue(sessionId);

        assertEquals(0, created);
        verify(jobs, never()).save(any(AnalysisJobEntity.class));
    }

    @Test
    void skipsAnswersWithoutRecordedMedia() {
        answer.setMediaKey(null);
        when(answers.findAllBySessionIdOrderByQuestionIndexAsc(sessionId)).thenReturn(List.of(answer));
        when(answers.findById(answerId)).thenReturn(Optional.of(answer));

        assertEquals(0, service(stubAsr("..."), stubEvaluation(), 3).enqueue(sessionId));
        verify(jobs, never()).save(any(AnalysisJobEntity.class));
    }

    @Test
    void queuesASingleAnswerOnItsOwn() {
        when(answers.findById(answerId)).thenReturn(Optional.of(answer));
        when(jobs.findByAnswerId(answerId)).thenReturn(Optional.empty());

        boolean queued = service(stubAsr("..."), stubEvaluation(), 3).enqueueAnswer(answerId);

        assertTrue(queued);
        verify(jobs).save(any(AnalysisJobEntity.class));
    }

    @Test
    void doesNotQueueTheSameAnswerTwice() {
        when(answers.findById(answerId)).thenReturn(Optional.of(answer));
        when(jobs.findByAnswerId(answerId)).thenReturn(Optional.of(pendingJob()));

        assertFalse(service(stubAsr("..."), stubEvaluation(), 3).enqueueAnswer(answerId));
        verify(jobs, never()).save(any(AnalysisJobEntity.class));
    }

    @Test
    void doesNotQueueAnAnswerWithoutMedia() {
        answer.setMediaKey(null);
        when(answers.findById(answerId)).thenReturn(Optional.of(answer));

        assertFalse(service(stubAsr("..."), stubEvaluation(), 3).enqueueAnswer(answerId));
        verify(jobs, never()).save(any(AnalysisJobEntity.class));
    }

    @Test
    void transcribesAndEvaluatesAPendingJob() {
        AnalysisJobEntity job = pendingJob();
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        when(answers.findById(answerId)).thenReturn(Optional.of(answer));
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        stubStoredMedia();

        service(stubAsr("我主导了订单系统的重构"), stubEvaluation(), 3).process(job.getId());

        assertEquals(AnalysisJobService.STATUS_COMPLETED, job.getStatus());
        assertEquals("我主导了订单系统的重构", answer.getTranscriptText());
        assertEquals("completed", answer.getTranscriptStatus());
        assertEquals("completed", answer.getEvaluationStatus());
        assertTrue(answer.getEvaluationJson().contains("\"overallScore\":82.5"));
        assertTrue(answer.getEvaluationJson().contains("raw-model-output"),
                "报告的原始模型响应必须落库");
    }

    @Test
    void marksTheJobFailedOnceMaxAttemptsIsReached() {
        AnalysisJobEntity job = pendingJob();
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        when(answers.findById(answerId)).thenReturn(Optional.of(answer));
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        stubStoredMedia();

        AsrProvider failing = (path, contentType) -> {
            throw new IOException("asr provider is down");
        };

        AnalysisJobService service = service(failing, stubEvaluation(), 1);

        assertThrows(IllegalStateException.class, () -> service.process(job.getId()));

        assertEquals(AnalysisJobService.STATUS_FAILED, job.getStatus());
        assertEquals(1, job.getAttempts());
        assertTrue(job.getLastError().contains("asr provider is down"));
    }

    @Test
    void keepsTheJobPendingUntilMaxAttemptsIsReached() {
        AnalysisJobEntity job = pendingJob();
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        when(answers.findById(answerId)).thenReturn(Optional.of(answer));
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        stubStoredMedia();

        AsrProvider failing = (path, contentType) -> {
            throw new IOException("asr provider is down");
        };

        AnalysisJobService service = service(failing, stubEvaluation(), 3);

        assertThrows(IllegalStateException.class, () -> service.process(job.getId()));

        assertEquals(AnalysisJobService.STATUS_PENDING, job.getStatus());
        assertEquals(1, job.getAttempts());
    }

    @Test
    void redactsSecretsFromThePersistedProviderError() {
        AnalysisJobEntity job = pendingJob();
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        when(answers.findById(answerId)).thenReturn(Optional.of(answer));
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        stubStoredMedia();

        AsrProvider failing = (media, contentType) -> {
            throw new IOException("upstream rejected api-key=sk-live-9f8e7d6c5b4a");
        };

        AnalysisJobService service = service(failing, stubEvaluation(), 1);

        assertThrows(IllegalStateException.class, () -> service.process(job.getId()));

        assertTrue(job.getLastError().contains("upstream rejected"), job.getLastError());
        assertFalse(job.getLastError().contains("sk-live-9f8e7d6c5b4a"), job.getLastError());
    }

    @Test
    void skipsJobsThatAnotherInstanceAlreadyClaimed() {
        AnalysisJobEntity job = pendingJob();
        when(jobs.findAllByStatusOrderByCreatedAtAsc(AnalysisJobService.STATUS_PENDING))
                .thenReturn(List.of(job));
        when(jobs.transitionStatus(eq(job.getId()), anyString(), anyString(), any())).thenReturn(0);

        service(stubAsr("..."), stubEvaluation(), 3).processPending();

        assertEquals(AnalysisJobService.STATUS_PENDING, job.getStatus());
        assertNull(job.getLastError());
        verify(answers, never()).findById(any());
    }

    @Test
    void requeuesJobsStuckInProcessing() {
        AnalysisJobEntity stuck = pendingJob();
        stuck.setStatus(AnalysisJobService.STATUS_PROCESSING);
        when(jobs.findAllByStatusAndUpdatedAtBefore(eq(AnalysisJobService.STATUS_PROCESSING), any()))
                .thenReturn(List.of(stuck));
        when(jobs.findAllByStatusOrderByCreatedAtAsc(AnalysisJobService.STATUS_PENDING))
                .thenReturn(List.of());

        service(stubAsr("..."), stubEvaluation(), 3).processPending();

        assertEquals(AnalysisJobService.STATUS_PENDING, stuck.getStatus());
        assertEquals(1, stuck.getAttempts());
        assertTrue(stuck.getLastError().contains("重新排队"), stuck.getLastError());
    }

    @Test
    void requeuesFailedJobsForRetry() {
        AnalysisJobEntity failed = pendingJob();
        failed.setStatus(AnalysisJobService.STATUS_FAILED);
        failed.setAttempts(3);
        failed.setLastError("boom");
        AnalysisJobEntity done = pendingJob();
        done.setStatus(AnalysisJobService.STATUS_COMPLETED);
        when(jobs.findAllBySessionId(sessionId)).thenReturn(List.of(failed, done));

        int requeued = service(stubAsr("..."), stubEvaluation(), 3).retryFailed(sessionId);

        assertEquals(1, requeued);
        assertEquals(AnalysisJobService.STATUS_PENDING, failed.getStatus());
        assertEquals(0, failed.getAttempts());
        assertNull(failed.getLastError());
        assertEquals(AnalysisJobService.STATUS_COMPLETED, done.getStatus());
    }

    @Test
    void timesOutAHangingProviderInsteadOfBlockingForever() {
        AnalysisJobEntity job = pendingJob();
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        when(answers.findById(answerId)).thenReturn(Optional.of(answer));
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        stubStoredMedia();

        AsrProvider hanging = (media, contentType) -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return TranscriptResult.ofText("too late");
        };

        AnalysisJobService service = service(hanging, stubEvaluation(), 1, 300);

        long startedAt = System.currentTimeMillis();
        assertThrows(IllegalStateException.class, () -> service.process(job.getId()));
        long elapsed = System.currentTimeMillis() - startedAt;

        assertTrue(elapsed < 3_000, "应当在毫秒级超时，实际耗时 " + elapsed + " ms");
        assertTrue(job.getLastError().contains("超时"), job.getLastError());
    }

    private AnalysisJobService service(AsrProvider asr, EvaluationProvider evaluation, int maxAttempts) {
        return service(asr, evaluation, maxAttempts, 60_000);
    }

    private AnalysisJobService service(AsrProvider asr, EvaluationProvider evaluation, int maxAttempts,
                                       long providerTimeoutMs) {
        return new AnalysisJobService(jobs, sessions, answers, mediaStorage, asr, evaluation, validator,
                new PracticeQuestionCatalog(objectMapper), maxAttempts, providerTimeoutMs);
    }

    private void stubStoredMedia() {
        when(mediaStorage.find("media-key"))
                .thenReturn(Optional.of(new ByteArrayResource(new byte[] {1, 2, 3})));
    }

    private AnalysisJobEntity pendingJob() {
        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setId(UUID.randomUUID());
        job.setSessionId(sessionId);
        job.setAnswerId(answerId);
        job.setStatus(AnalysisJobService.STATUS_PENDING);
        job.setAttempts(0);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        return job;
    }

    private static AsrProvider stubAsr(String text) {
        return (path, contentType) -> TranscriptResult.ofText(text);
    }

    private static EvaluationProvider stubEvaluation() {
        EvaluationResult result = new EvaluationResult(
                82.5,
                new EvaluationResult.DimensionScores(88, 80, 85, 75, 84),
                new EvaluationResult.DeliveryMetrics(210.5, 4, 7.5, 3),
                List.of("多用 STAR 结构描述项目"),
                "先讲背景，再讲任务与行动。",
                List.of(new EvaluationResult.Evidence("relevance", "我主导了订单系统的重构", 0, 5)));
        return new EvaluationProvider() {
            @Override
            public EvaluationOutcome evaluate(EvaluationRequest request) {
                return new EvaluationOutcome(result, RAW_RESPONSE);
            }

            @Override
            public EvaluationProvenance provenance() {
                return new EvaluationProvenance("qwen-max", "qwen-max", "practice-evaluation-v1");
            }
        };
    }
}
