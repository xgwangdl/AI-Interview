package com.guangge.Interview.cohort;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssignmentView(
        UUID id,
        UUID cohortId,
        String title,
        String description,
        int days,
        int itemsPerDay,
        Instant dueAt,
        Instant createdAt,
        int memberCount,
        int completedMembers,
        int myTotal,
        int myCompleted,
        List<MemberProgress> progress) {

    public record MemberProgress(String owner, String role, boolean shareScores,
                                 int completed, int total, double percent, Double averageScore) { }
}
