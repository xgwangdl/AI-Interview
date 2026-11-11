package com.guangge.Interview.jobradar;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobRadarWorkerHeartbeatTest {
    @Test
    void longRunningTaskKeepsRenewingItsLease() throws Exception {
        JobAgentRunService runs = mock(JobAgentRunService.class);
        JobEvaluationRunner evaluations = mock(JobEvaluationRunner.class);
        DebriefRunner debriefs = mock(DebriefRunner.class);
        JobAgentRunEntity run = new JobAgentRunEntity();
        run.setId(UUID.randomUUID());
        run.setOwnerId("alice");
        run.setKind(JobAgentRunService.KIND_JOB_EVALUATE);
        run.setSubjectId(UUID.randomUUID());
        run.setAttempts(1);
        run.setCreatedAt(Instant.now());
        run.setUpdatedAt(Instant.now());
        when(runs.claimNext()).thenReturn(Optional.of(run), Optional.empty());

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            started.countDown();
            assertThat(release.await(4, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(evaluations).evaluate(run);

        JobRadarWorker worker = new JobRadarWorker(runs, evaluations, debriefs, true, 1, 1000);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> poll = executor.submit(worker::poll);
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            verify(runs, timeout(2500)).heartbeat(run.getId());
            release.countDown();
            poll.get(2, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            worker.shutdownHeartbeatScheduler();
            executor.shutdownNow();
        }
    }
}
