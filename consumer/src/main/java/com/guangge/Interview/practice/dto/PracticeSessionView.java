package com.guangge.Interview.practice.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PracticeSessionView(
        UUID id,
        UUID goalId,
        String role,
        String company,
        String jobDescription,
        String resumeName,
        String interviewType,
        String difficulty,
        String interviewerStyle,
        int questionCount,
        int durationMinutes,
        boolean followUpEnabled,
        List<PracticeQuestion> questions,
        String status,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant retentionUntil,
        List<PracticeAnswerView> answers) {
}
