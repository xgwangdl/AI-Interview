package com.guangge.Interview.goal;

import java.util.UUID;

public record PlanItemView(
        UUID id,
        String kind,
        String title,
        int estimatedMinutes,
        String status,
        UUID sessionId,
        int position) {
}
