package com.guangge.Interview.jobradar;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record JobEvaluationView(
        UUID id,
        int score,
        String confidence,
        JsonNode breakdown,
        JsonNode hits,
        JsonNode gaps,
        String reason,
        String recommendation,
        boolean degraded,
        String model,
        Instant createdAt) { }
