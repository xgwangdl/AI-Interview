package com.guangge.Interview.jobradar;

import java.time.Instant;
import java.util.UUID;

public record JobLeadSummaryView(
        UUID id,
        String company,
        String role,
        String city,
        Integer salaryMinK,
        Integer salaryMaxK,
        String status,
        Integer latestScore,
        String latestRecommendation,
        String evaluationRunStatus,
        Instant createdAt,
        Instant updatedAt) { }
