package com.guangge.Interview.practice.dto;

import java.time.Instant;
import java.util.UUID;

public record PracticeAnswerView(
        UUID id,
        int questionIndex,
        String questionId,
        int attempt,
        boolean isCurrent,
        String mediaContentType,
        Long mediaSizeBytes,
        Integer durationSeconds,
        Integer startedAtOffsetSeconds,
        String transcriptStatus,
        String transcriptText,
        String transcriptOriginal,
        Instant transcriptEditedAt,
        String evaluationStatus,
        String rewriteText,
        Instant createdAt) {
}
