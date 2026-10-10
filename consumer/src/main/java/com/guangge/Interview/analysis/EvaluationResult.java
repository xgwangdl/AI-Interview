package com.guangge.Interview.analysis;

import java.util.List;

/**
 * Structured scoring result for a single answer. This is the fixed JSON schema the
 * model must satisfy; free-form model output is rejected by {@link EvaluationResultValidator}.
 */
public record EvaluationResult(
        double overallScore,
        DimensionScores scores,
        DeliveryMetrics delivery,
        List<String> improvementSuggestions,
        String sampleAnswer,
        List<Evidence> evidence) {

    public record DimensionScores(
            double relevance,
            double completeness,
            double professionalism,
            double starStructure,
            double logic) { }

    public record DeliveryMetrics(
            double speechRateCharactersPerMinute,
            int pauseCount,
            double totalPauseSeconds,
            int fillerWordCount) { }

    /** Evidence is anchored to a playback offset so the report can jump into the video. */
    public record Evidence(String dimension, String quote, double startSeconds, double endSeconds) { }
}
