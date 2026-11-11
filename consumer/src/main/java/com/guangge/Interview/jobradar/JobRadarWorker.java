package com.guangge.Interview.jobradar;

import com.guangge.Interview.util.LogSanitizer;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class JobRadarWorker {
    private static final Logger log = LoggerFactory.getLogger(JobRadarWorker.class);

    private final JobAgentRunService runs;
    private final JobEvaluationRunner evaluations;
    private final DebriefRunner debriefs;
    private final boolean enabled;
    private final int maxConcurrency;
    private final long heartbeatIntervalMs;
    private final ScheduledExecutorService heartbeatScheduler;

    public JobRadarWorker(
            JobAgentRunService runs,
            JobEvaluationRunner evaluations,
            DebriefRunner debriefs,
            @Value("${practice.agent.enabled:true}") boolean enabled,
            @Value("${practice.agent.max-concurrency:1}") int maxConcurrency,
            @Value("${practice.agent.heartbeat-interval-ms:30000}") long heartbeatIntervalMs) {
        this.runs = runs;
        this.evaluations = evaluations;
        this.debriefs = debriefs;
        this.enabled = enabled;
        this.maxConcurrency = Math.max(1, Math.min(2, maxConcurrency));
        this.heartbeatIntervalMs = Math.max(1000, heartbeatIntervalMs);
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "job-radar-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Scheduled(
            fixedDelayString = "${practice.agent.poll-interval-ms:5000}",
            initialDelayString = "${practice.agent.initial-delay-ms:15000}")
    public void poll() {
        if (!enabled) return;
        for (int i = 0; i < maxConcurrency; i++) {
            Optional<JobAgentRunEntity> claimed = runs.claimNext();
            if (claimed.isEmpty()) return;
            execute(claimed.get());
        }
    }

    private void execute(JobAgentRunEntity run) {
        ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(
                () -> renewLease(run.getId()), heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
        try {
            if (JobAgentRunService.KIND_JOB_EVALUATE.equals(run.getKind())) {
                evaluations.evaluate(run);
                return;
            }
            if (JobAgentRunService.KIND_DEBRIEF_PARSE.equals(run.getKind())) {
                debriefs.parse(run);
                return;
            }
            recoverIfTerminal(run, runs.failOrRetry(run.getId(), "尚未支持的 Agent 任务类型：" + run.getKind()));
        } catch (JobRunDeferredException deferred) {
            runs.defer(run.getId(), deferred.resumeAt(), deferred.getMessage());
        } catch (RuntimeException exception) {
            String safe = LogSanitizer.sanitize(exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage());
            log.warn("Agent 任务失败 run={} kind={} attempts={}: {}",
                    run.getId(), run.getKind(), run.getAttempts(), safe);
            recoverIfTerminal(run, runs.failOrRetry(run.getId(), safe));
        } finally {
            heartbeat.cancel(false);
        }
    }

    private void renewLease(UUID runId) {
        try {
            runs.heartbeat(runId);
        } catch (RuntimeException exception) {
            log.warn("Agent 任务心跳失败 run={}: {}", runId, LogSanitizer.sanitize(exception.getMessage()));
        }
    }

    private void recoverIfTerminal(JobAgentRunEntity run, JobAgentRunService.FailureResult failure) {
        if (JobAgentRunService.STATUS_FAILED.equals(failure.status())) {
            evaluations.recoverTerminalFailure(run);
        }
    }

    @PreDestroy
    void shutdownHeartbeatScheduler() {
        heartbeatScheduler.shutdownNow();
    }
}
