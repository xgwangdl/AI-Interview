package com.guangge.Interview.jobradar;

import java.time.Instant;
import java.util.UUID;

public record JobLeadView(
        UUID id,
        String company,
        String role,
        String city,
        Integer salaryMinK,
        Integer salaryMaxK,
        String jdText,
        String status,
        String sourceUrl,
        String note,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt,
        JobEvaluationView evaluation,
        String evaluationRunStatus) { }
