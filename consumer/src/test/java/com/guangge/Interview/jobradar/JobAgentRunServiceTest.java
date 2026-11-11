package com.guangge.Interview.jobradar;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobAgentRunServiceTest {
    private JobAgentRunRepository runs;
    private EntityManager entityManager;
    private JobAgentRunService service;

    @BeforeEach
    void setUp() {
        runs = mock(JobAgentRunRepository.class);
        entityManager = mock(EntityManager.class);
        service = new JobAgentRunService(runs, new ObjectMapper(), entityManager, 120, 3, 1000, 8000, 90);
    }

    @Test
    void enqueueReturnsExistingIdempotentRun() {
        JobAgentRunEntity existing = run(JobAgentRunService.STATUS_PENDING, 0);
        when(runs.findByIdempotencyKey("same")).thenReturn(Optional.of(existing));

        var result = service.enqueue("owner", JobAgentRunService.KIND_JOB_EVALUATE,
                "job_lead", UUID.randomUUID(), "same", new ObjectMapper().createObjectNode());

        assertThat(result.duplicated()).isTrue();
        assertThat(result.run()).isSameAs(existing);
    }

    @Test
    void claimUsesLeaseRecoveryAndIncrementsAttemptsAtomically() {
        UUID id = UUID.randomUUID();
        JobAgentRunEntity claimed = run(JobAgentRunService.STATUS_PENDING, 0);
        claimed.setId(id);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenAnswer(invocation -> {
            claimed.setStatus(JobAgentRunService.STATUS_RUNNING);
            claimed.setAttempts(1);
            claimed.setStartedAt(Instant.now());
            return List.of(id);
        });
        when(runs.findById(id)).thenReturn(Optional.of(claimed));

        Optional<JobAgentRunEntity> result = service.claimNext();

        assertThat(result).containsSame(claimed);
        assertThat(claimed.getStatus()).isEqualTo(JobAgentRunService.STATUS_RUNNING);
        assertThat(claimed.getAttempts()).isEqualTo(1);
        assertThat(claimed.getStartedAt()).isNotNull();
        verify(runs).reclaimExpiredLeases(any(Instant.class));
        verify(entityManager).clear();
    }

    @Test
    void quotaDeferralDoesNotConsumeAnAttempt() {
        JobAgentRunEntity run = run(JobAgentRunService.STATUS_RUNNING, 1);
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));

        service.defer(run.getId(), Instant.now().plusSeconds(60), "quota");

        assertThat(run.getStatus()).isEqualTo(JobAgentRunService.STATUS_DEFERRED);
        assertThat(run.getAttempts()).isZero();
    }

    @Test
    void terminalFailureIsReportedAfterMaximumAttempts() {
        JobAgentRunEntity run = run(JobAgentRunService.STATUS_RUNNING, 3);
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));

        var result = service.failOrRetry(run.getId(), "provider down");

        assertThat(result.status()).isEqualTo(JobAgentRunService.STATUS_FAILED);
        assertThat(run.getStatus()).isEqualTo(JobAgentRunService.STATUS_FAILED);
        assertThat(run.getFinishedAt()).isNotNull();
    }

    private static JobAgentRunEntity run(String status, int attempts) {
        JobAgentRunEntity run = new JobAgentRunEntity();
        run.setId(UUID.randomUUID());
        run.setOwnerId("owner");
        run.setKind(JobAgentRunService.KIND_JOB_EVALUATE);
        run.setStatus(status);
        run.setAttempts(attempts);
        run.setCreatedAt(Instant.now());
        run.setUpdatedAt(Instant.now());
        return run;
    }
}
