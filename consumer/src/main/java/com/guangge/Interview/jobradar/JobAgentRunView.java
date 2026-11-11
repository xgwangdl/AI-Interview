package com.guangge.Interview.jobradar;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record JobAgentRunView(
        UUID id,
        String kind,
        String status,
        Instant createdAt,
        Long durationMs,
        int modelCalls,
        String error) {

    static JobAgentRunView from(JobAgentRunEntity run, boolean includeError) {
        Long duration = null;
        if (run.getStartedAt() != null) {
            Instant end = run.getFinishedAt() == null ? Instant.now() : run.getFinishedAt();
            duration = Math.max(0, Duration.between(run.getStartedAt(), end).toMillis());
        }
        return new JobAgentRunView(run.getId(), run.getKind(), run.getStatus(), run.getCreatedAt(), duration,
                run.getModelCalls(), includeError ? run.getError() : null);
    }
}
