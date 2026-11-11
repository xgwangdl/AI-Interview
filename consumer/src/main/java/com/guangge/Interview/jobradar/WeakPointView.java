package com.guangge.Interview.jobradar;

import java.time.Instant;
import java.util.UUID;

public record WeakPointView(
        UUID id,
        String source,
        String topic,
        String dimensionKey,
        String evidence,
        int weight,
        String status,
        Instant createdAt,
        Instant closedAt,
        String closedBy) { }
