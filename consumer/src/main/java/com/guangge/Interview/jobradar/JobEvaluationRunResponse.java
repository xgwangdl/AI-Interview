package com.guangge.Interview.jobradar;

import java.util.UUID;

public record JobEvaluationRunResponse(
        UUID leadId,
        UUID runId,
        String status,
        boolean reused,
        JobEvaluationView evaluation) { }
