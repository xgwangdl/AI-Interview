package com.guangge.Interview.jobradar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class JobAgentRunService {
    public static final String KIND_JOB_EVALUATE = "JOB_EVALUATE";
    public static final String KIND_DEBRIEF_PARSE = "DEBRIEF_PARSE";
    public static final String KIND_PRACTICE_FROM_JD = "PRACTICE_FROM_JD";
    public static final String KIND_WEAKPOINT_RECONCILE = "WEAKPOINT_RECONCILE";

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SUCCEEDED = "succeeded";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_DEFERRED = "deferred";
    public static final String STATUS_CANCELLED = "cancelled";

    private final JobAgentRunRepository runs;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final Duration leaseDuration;
    private final int maxAttempts;
    private final long retryBackoffMs;
    private final long retryBackoffMaxMs;
    private final int retentionDays;

    public JobAgentRunService(
            JobAgentRunRepository runs,
            ObjectMapper objectMapper,
            EntityManager entityManager,
            @Value("${practice.agent.lease-seconds:120}") long leaseSeconds,
            @Value("${practice.agent.max-attempts:3}") int maxAttempts,
            @Value("${practice.agent.retry-backoff-ms:5000}") long retryBackoffMs,
            @Value("${practice.agent.retry-backoff-max-ms:300000}") long retryBackoffMaxMs,
            @Value("${practice.agent.retention-days:90}") int retentionDays) {
        this.runs = runs;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
        this.leaseDuration = Duration.ofSeconds(Math.max(30, leaseSeconds));
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoffMs = Math.max(0, retryBackoffMs);
        this.retryBackoffMaxMs = Math.max(this.retryBackoffMs, retryBackoffMaxMs);
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Transactional
    public EnqueueResult enqueue(String ownerId, String kind, String subjectType, UUID subjectId,
                                 String idempotencyKey, JsonNode input) {
        Optional<JobAgentRunEntity> existing = runs.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return new EnqueueResult(existing.get(), true);
        Optional<JobAgentRunEntity> active = runs
                .findFirstByOwnerIdAndKindAndSubjectIdAndStatusInOrderByCreatedAtDesc(ownerId, kind, subjectId,
                        List.of(STATUS_PENDING, STATUS_RUNNING, STATUS_DEFERRED));
        if (active.isPresent()) return new EnqueueResult(active.get(), true);

        Instant now = Instant.now();
        JobAgentRunEntity run = new JobAgentRunEntity();
        run.setId(UUID.randomUUID());
        run.setOwnerId(ownerId);
        run.setKind(kind);
        run.setStatus(STATUS_PENDING);
        run.setIdempotencyKey(idempotencyKey);
        run.setSubjectType(subjectType);
        run.setSubjectId(subjectId);
        run.setInputJson(input);
        run.setAttempts(0);
        run.setModelCalls(0);
        run.setMinutesSaved(0);
        run.setNextAttemptAt(now);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        try {
            return new EnqueueResult(runs.saveAndFlush(run), false);
        } catch (DataIntegrityViolationException concurrentInsert) {
            JobAgentRunEntity winner = runs.findByIdempotencyKey(idempotencyKey)
                    .orElseGet(() -> runs.findFirstByOwnerIdAndKindAndSubjectIdAndStatusInOrderByCreatedAtDesc(
                                    ownerId, kind, subjectId,
                                    List.of(STATUS_PENDING, STATUS_RUNNING, STATUS_DEFERRED))
                            .orElseThrow(() -> concurrentInsert));
            return new EnqueueResult(winner, true);
        }
    }

    /** Atomically claim one due run using PostgreSQL row locking and SKIP LOCKED. */
    @Transactional
    public Optional<JobAgentRunEntity> claimNext() {
        Instant now = Instant.now();
        runs.reclaimExpiredLeases(now);
        String sql = """
                with candidate as (
                    select id from job_agent_runs
                    where status in ('pending', 'deferred')
                      and (next_attempt_at is null or next_attempt_at <= :now)
                    order by created_at asc
                    for update skip locked
                    limit 1
                )
                update job_agent_runs run
                   set status = 'running',
                       attempts = attempts + 1,
                       started_at = coalesce(started_at, :now),
                       lease_expires_at = :leaseUntil,
                       last_heartbeat_at = :now,
                       updated_at = :now
                  from candidate
                 where run.id = candidate.id
                 returning run.id
                """;
        @SuppressWarnings("unchecked")
        List<Object> ids = entityManager.createNativeQuery(sql)
                .setParameter("now", now)
                .setParameter("leaseUntil", now.plus(leaseDuration))
                .getResultList();
        if (ids.isEmpty()) return Optional.empty();
        // The PostgreSQL update bypasses JPA, so discard a stale pending/running entity
        // before loading the claimed row. Without this, a long-lived persistence context
        // can hand the worker the pre-claim status or attempts count.
        entityManager.clear();
        UUID id = ids.getFirst() instanceof UUID uuid ? uuid : UUID.fromString(ids.getFirst().toString());
        return runs.findById(id);
    }

    @Transactional
    public void heartbeat(UUID runId) {
        runs.findById(runId).ifPresent(run -> {
            Instant now = Instant.now();
            run.setLastHeartbeatAt(now);
            run.setLeaseExpiresAt(now.plus(leaseDuration));
            run.setUpdatedAt(now);
            runs.save(run);
        });
    }

    @Transactional
    public void complete(UUID runId, JsonNode output, Collection<AgentStep> steps,
                         int modelCalls, int minutesSaved) {
        JobAgentRunEntity run = requireRun(runId);
        Instant now = Instant.now();
        run.setStatus(STATUS_SUCCEEDED);
        run.setOutputJson(output);
        run.setStepsJson(objectMapper.valueToTree(steps));
        run.setModelCalls(Math.max(0, modelCalls));
        run.setMinutesSaved(Math.max(0, minutesSaved));
        run.setFinishedAt(now);
        run.setLeaseExpiresAt(null);
        run.setError(null);
        run.setUpdatedAt(now);
        runs.save(run);
    }

    @Transactional
    public void defer(UUID runId, Instant resumeAt, String reason) {
        JobAgentRunEntity run = requireRun(runId);
        Instant now = Instant.now();
        // Deferred retries are quota waits, not failures: do not consume an attempt.
        run.setAttempts(Math.max(0, run.getAttempts() - 1));
        run.setStatus(STATUS_DEFERRED);
        run.setNextAttemptAt(resumeAt == null ? now.plusSeconds(60) : resumeAt);
        run.setLeaseExpiresAt(null);
        run.setError(reason);
        run.setUpdatedAt(now);
        runs.save(run);
    }

    @Transactional
    public FailureResult failOrRetry(UUID runId, String error) {
        JobAgentRunEntity run = requireRun(runId);
        Instant now = Instant.now();
        run.setError(error == null ? "未知错误" : error.length() <= 2000 ? error : error.substring(0, 2000));
        run.setLeaseExpiresAt(null);
        run.setUpdatedAt(now);
        if (run.getAttempts() >= maxAttempts) {
            run.setStatus(STATUS_FAILED);
            run.setFinishedAt(now);
            run.setNextAttemptAt(null);
            runs.save(run);
            return new FailureResult(STATUS_FAILED, null);
        }
        long delay = backoffMillis(run.getAttempts());
        Instant next = now.plusMillis(delay);
        run.setStatus(STATUS_PENDING);
        run.setNextAttemptAt(next);
        runs.save(run);
        return new FailureResult(STATUS_PENDING, next);
    }

    @Transactional
    public void cancelForDeletedSubject(UUID subjectId) {
        runs.cancelForDeletedSubject(subjectId, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<JobAgentRunView> recent(String ownerId, int limit) {
        return runs.findTop20ByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .limit(Math.max(1, Math.min(20, limit)))
                .map(run -> JobAgentRunView.from(run, true))
                .toList();
    }

    private JobAgentRunEntity requireRun(UUID id) {
        return runs.findById(id).orElseThrow(() -> new IllegalStateException("Agent run not found: " + id));
    }

    private long backoffMillis(int attempts) {
        if (retryBackoffMs == 0) return 0;
        long delay = retryBackoffMs;
        for (int i = 1; i < attempts && delay < retryBackoffMaxMs; i++) {
            delay = delay > retryBackoffMaxMs / 2 ? retryBackoffMaxMs : delay * 2;
        }
        return Math.min(delay, retryBackoffMaxMs);
    }

    @Scheduled(cron = "${practice.agent.cleanup-cron:0 43 3 * * *}")
    @Transactional
    public void cleanup() {
        runs.deleteAllByStatusInAndFinishedAtBefore(
                List.of(STATUS_SUCCEEDED, STATUS_FAILED, STATUS_CANCELLED),
                Instant.now().minus(Duration.ofDays(retentionDays)));
    }

    public record EnqueueResult(JobAgentRunEntity run, boolean duplicated) { }
    public record FailureResult(String status, Instant retryAt) { }
}
