package com.guangge.Interview.analysis;

import java.time.Instant;
import java.util.UUID;

/** 死信队列条目的对外视图。 */
public record AnalysisDlqView(
        UUID jobId,
        UUID sessionId,
        UUID answerId,
        int attempts,
        String lastError,
        Instant originalCreatedAt,
        Instant movedAt) {

    public static AnalysisDlqView from(AnalysisDlqEntity entity) {
        return new AnalysisDlqView(
                entity.getId(),
                entity.getSessionId(),
                entity.getAnswerId(),
                entity.getAttempts(),
                entity.getLastError(),
                entity.getOriginalCreatedAt(),
                entity.getMovedAt());
    }
}
