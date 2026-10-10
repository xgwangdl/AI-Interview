package com.guangge.Interview.analysis;

import com.guangge.Interview.practice.entity.PracticeAnswerEntity;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.repository.PracticeAnswerRepository;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import com.guangge.Interview.practice.service.PracticeQuestionCatalog;
import com.guangge.Interview.practice.storage.MediaStorage;
import com.guangge.Interview.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.UUID;

/**
 * Runs the asynchronous transcription + evaluation pipeline for recorded answers.
 * Jobs are idempotent: an answer is only transcribed or evaluated once.
 */
@Service
public class AnalysisJobService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisJobService.class);
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";
    private static final int MAX_RAW_RESPONSE_LENGTH = 4000;
    /** 超过这个时长仍是 processing 的任务，视为进程中断，回收重排。 */
    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(10);

    private final AnalysisJobRepository jobs;
    private final PracticeSessionRepository sessions;
    private final PracticeAnswerRepository answers;
    private final MediaStorage mediaStorage;
    private final AsrProvider asrProvider;
    private final EvaluationProvider evaluationProvider;
    private final EvaluationResultValidator validator;
    private final PracticeQuestionCatalog questions;
    private final int maxAttempts;
    private final long providerTimeoutMs;

    public AnalysisJobService(
            AnalysisJobRepository jobs,
            PracticeSessionRepository sessions,
            PracticeAnswerRepository answers,
            MediaStorage mediaStorage,
            AsrProvider asrProvider,
            EvaluationProvider evaluationProvider,
            EvaluationResultValidator validator,
            PracticeQuestionCatalog questions,
            @Value("${practice.analysis.max-attempts:3}") int maxAttempts,
            @Value("${practice.analysis.provider-timeout-ms:120000}") long providerTimeoutMs) {
        this.jobs = jobs;
        this.sessions = sessions;
        this.answers = answers;
        this.mediaStorage = mediaStorage;
        this.asrProvider = asrProvider;
        this.evaluationProvider = evaluationProvider;
        this.validator = validator;
        this.questions = questions;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.providerTimeoutMs = Math.max(1000, providerTimeoutMs);
    }

    /** Creates one pending job per recorded answer, skipping answers already queued. */
    @Transactional
    public int enqueue(UUID sessionId) {
        int created = 0;
        for (PracticeAnswerEntity answer : answers.findAllBySessionIdOrderByQuestionIndexAsc(sessionId)) {
            if (enqueueAnswer(answer.getId())) created++;
        }
        return created;
    }

    /**
     * 单题入队：每题上传完成后就可以开始分析，不必等整场练习结束。
     * 已经排过队的答案不会重复创建（整场结束时再调一次也不会重复）。
     */
    @Transactional
    public boolean enqueueAnswer(UUID answerId) {
        PracticeAnswerEntity answer = answers.findById(answerId).orElse(null);
        if (answer == null || answer.getMediaKey() == null) return false;
        if (jobs.findByAnswerId(answerId).isPresent()) return false;
        Instant now = Instant.now();
        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setId(UUID.randomUUID());
        job.setSessionId(answer.getSessionId());
        job.setAnswerId(answerId);
        job.setStatus(STATUS_PENDING);
        job.setAttempts(0);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        jobs.save(job);
        return true;
    }

    @Scheduled(
            fixedDelayString = "${practice.analysis.poll-interval-ms:15000}",
            initialDelayString = "${practice.analysis.initial-delay-ms:30000}")
    @Transactional
    public void processPending() {
        reclaimStuckJobs();
        for (AnalysisJobEntity job : jobs.findAllByStatusOrderByCreatedAtAsc(STATUS_PENDING)) {
            if (!claim(job.getId())) {
                continue;
            }
            job.setStatus(STATUS_PROCESSING);
            try {
                run(job);
            } catch (RuntimeException exception) {
                log.warn("Analysis job {} failed: {}", job.getId(), exception.getMessage());
            }
        }
    }

    /** Processes a single job; used for retries and manual triggering. */
    @Transactional
    public void process(UUID jobId) {
        AnalysisJobEntity job = jobs.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Analysis job no longer exists"));
        if (STATUS_COMPLETED.equals(job.getStatus())) return;
        job.setStatus(STATUS_PROCESSING);
        run(job);
    }

    private boolean claim(UUID jobId) {
        return jobs.transitionStatus(jobId, STATUS_PENDING, STATUS_PROCESSING, Instant.now()) == 1;
    }

    /**
     * 把失败的任务重新排队，供用户手动重试。
     *
     * failed 是终态，调度器只认 pending；没有这一步，失败的任务就永远停在 failed，
     * 报告上的「稍后会重新排队」也就成了空话。
     */
    @Transactional
    public int retryFailed(UUID sessionId) {
        int requeued = 0;
        for (AnalysisJobEntity job : jobs.findAllBySessionId(sessionId)) {
            if (!STATUS_FAILED.equals(job.getStatus())) continue;
            job.setStatus(STATUS_PENDING);
            job.setAttempts(0);
            job.setLastError(null);
            job.setUpdatedAt(Instant.now());
            jobs.save(job);
            requeued++;
        }
        return requeued;
    }

    /**
     * 进程在处理途中被杀掉时，任务会一直停在 processing。
     * 这里把它当作一次「未记入的失败」重新排队，让 maxAttempts 真正起到兜底作用。
     */
    private void reclaimStuckJobs() {
        Instant cutoff = Instant.now().minus(PROCESSING_TIMEOUT);
        for (AnalysisJobEntity stuck : jobs.findAllByStatusAndUpdatedAtBefore(STATUS_PROCESSING, cutoff)) {
            int attempts = stuck.getAttempts() + 1;
            stuck.setAttempts(attempts);
            stuck.setLastError("处理超时或进程中断，已重新排队");
            stuck.setStatus(attempts >= maxAttempts ? STATUS_FAILED : STATUS_PENDING);
            stuck.setUpdatedAt(Instant.now());
            jobs.save(stuck);
            log.warn("回收卡住的分析任务 {}，attempts={}", stuck.getId(), attempts);
        }
    }

    private void run(AnalysisJobEntity job) {
        if (STATUS_COMPLETED.equals(job.getStatus())) return;
        try {
            runProviders(job);
            job.setStatus(STATUS_COMPLETED);
            job.setLastError(null);
            job.setUpdatedAt(Instant.now());
            jobs.save(job);
        } catch (RuntimeException exception) {
            job.setAttempts(job.getAttempts() + 1);
            job.setLastError(trimError(exception));
            job.setStatus(job.getAttempts() >= maxAttempts ? STATUS_FAILED : STATUS_PENDING);
            job.setUpdatedAt(Instant.now());
            jobs.save(job);
            throw exception;
        }
    }

    private void runProviders(AnalysisJobEntity job) {
        PracticeAnswerEntity answer = answers.findById(job.getAnswerId())
                .orElseThrow(() -> new IllegalStateException("Answer no longer exists: " + job.getAnswerId()));
        PracticeSessionEntity session = sessions.findById(job.getSessionId())
                .orElseThrow(() -> new IllegalStateException("Practice session no longer exists: " + job.getSessionId()));

        String transcript = answer.getTranscriptText();
        if (transcript == null || transcript.isBlank()) {
            Resource media = mediaStorage.find(answer.getMediaKey())
                    .orElseThrow(() -> new IllegalStateException("Recorded media is missing: " + job.getAnswerId()));
            transcript = transcribe(media, answer.getMediaContentType()).text();
            answer.setTranscriptText(transcript);
            answer.setTranscriptStatus("completed");
            answers.save(answer);
        }

        if (answer.getEvaluationJson() == null || answer.getEvaluationJson().isBlank()) {
            EvaluationRequest request = new EvaluationRequest(
                    session.getRole(),
                    session.getJobDescription(),
                    answer.getQuestionId(),
                    questionText(session, answer.getQuestionId()),
                    transcript,
                    answer.getDurationSeconds());
            EvaluationOutcome outcome = withTimeout("内容评分",
                    () -> evaluationProvider.evaluate(request));
            EvaluationProvenance provenance = evaluationProvider.provenance();
            EvaluationReport report = new EvaluationReport(
                    provenance.modelName(),
                    provenance.modelVersion(),
                    provenance.promptVersion(),
                    Instant.now(),
                    // 原始响应留档用于审计，但加上限，避免异常长的输出撑爆这一行。
                    LogSanitizer.truncate(outcome.rawResponse(), MAX_RAW_RESPONSE_LENGTH),
                    outcome.result());
            answer.setEvaluationJson(validator.write(report));
            answer.setEvaluationStatus("completed");
            answers.save(answer);
        }
    }

    private TranscriptResult transcribe(Resource media, String contentType) {
        return withTimeout("语音转写", () -> asrProvider.transcribe(media, contentType));
    }

    /**
     * 供应商调用加超时。
     *
     * 没有这一步，一次挂起的转写或评分会永久占住调度线程（调度器是单线程的），
     * 后续所有任务都跟着停摆。超时后任务按普通失败处理，走既有的重试与终态逻辑。
     */
    private <T> T withTimeout(String label, ProviderCall<T> call) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return call.get();
                } catch (Exception exception) {
                    throw new CompletionException(exception);
                }
            }).get(providerTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new IllegalStateException(label + "超时（" + (providerTimeoutMs / 1000) + " 秒）", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException(label + "失败", cause);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(label + "被中断", exception);
        }
    }

    @FunctionalInterface
    private interface ProviderCall<T> {
        T get() throws Exception;
    }

    private String questionText(PracticeSessionEntity session, String questionId) {
        return questions.questionText(session.getQuestionsJson(), questionId);
    }

    private static String trimError(RuntimeException exception) {
        String message = exception.getMessage();
        Throwable cause = exception.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            message = (message == null || message.isBlank() ? "" : message + ": ") + cause.getMessage();
        }
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        return LogSanitizer.truncate(LogSanitizer.redact(message), 1000);
    }
}
