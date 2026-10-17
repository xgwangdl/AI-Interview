package com.guangge.Interview.cohort;

import java.time.Instant;
import java.util.UUID;

public record PeerReviewReceivedView(
        UUID id,
        String reviewerAlias,
        int clarity,
        int relevance,
        int depth,
        String suggestion,
        Instant createdAt) {
}
