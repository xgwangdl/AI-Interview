package com.guangge.Interview.goal;

import java.time.Instant;
import java.util.UUID;

public record JobGoalView(
        UUID id,
        String company,
        String role,
        String jobDescription,
        UUID resumeId,
        String stage,
        Instant interviewAt,
        String status,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        /** 距面试天数；已过期为负数；没有日期为 null。 */
        Long daysUntilInterview,
        GoalProgressView progress) {
}
