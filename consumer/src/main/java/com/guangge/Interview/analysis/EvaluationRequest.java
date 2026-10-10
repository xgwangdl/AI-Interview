package com.guangge.Interview.analysis;

public record EvaluationRequest(
        String role,
        String jobDescription,
        String questionId,
        String questionText,
        String transcript,
        Integer durationSeconds) { }
