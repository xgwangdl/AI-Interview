package com.guangge.Interview.analysis;

import java.time.Instant;

/**
 * Persisted evaluation payload: model/prompt provenance, the raw model response,
 * and the schema-validated structured result.
 */
public record EvaluationReport(
        String modelName,
        String modelVersion,
        String promptVersion,
        Instant analyzedAt,
        String rawResponse,
        EvaluationResult result) { }
