package com.guangge.Interview.analysis;

public record EvaluationRequest(
        String role,
        String jobDescription,
        String questionId,
        String questionText,
        String questionCategory,
        String interviewType,
        String transcript,
        Integer durationSeconds) { }
