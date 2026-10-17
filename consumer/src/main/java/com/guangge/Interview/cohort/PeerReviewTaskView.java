package com.guangge.Interview.cohort;

import java.time.Instant;
import java.util.UUID;

public record PeerReviewTaskView(
        UUID answerId,
        String question,
        String transcript,
        String alias,
        Instant createdAt) {
}
