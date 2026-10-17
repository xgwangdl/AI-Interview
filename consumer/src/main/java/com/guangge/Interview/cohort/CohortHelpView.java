package com.guangge.Interview.cohort;

import java.time.Instant;
import java.util.UUID;

public record CohortHelpView(
        UUID id,
        String owner,
        String question,
        String transcript,
        String note,
        String status,
        String feedback,
        Instant createdAt,
        Instant answeredAt) {
}
