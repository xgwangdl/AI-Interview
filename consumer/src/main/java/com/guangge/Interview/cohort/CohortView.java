package com.guangge.Interview.cohort;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CohortView(
        UUID id,
        String name,
        String inviteCode,
        String role,
        int memberCount,
        Instant createdAt,
        List<Member> members) {

    public record Member(String owner, String role, boolean shareScores, Instant joinedAt) { }
}
