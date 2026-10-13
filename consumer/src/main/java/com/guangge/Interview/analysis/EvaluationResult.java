package com.guangge.Interview.analysis;

import java.util.List;

/**
 * Structured scoring result for a single answer. This is the fixed JSON schema the
 * model must satisfy; free-form model output is rejected by {@link EvaluationResultValidator}.
 *
 * 分数一律用包装类型：模型漏字段时必须能表达「缺失」，否则 Jackson 会把缺失的数值填成 0，
 * 而 0 本身是合法分数，范围校验拦不住——那就变成静默的 0 分。
 */
public record EvaluationResult(
        Double overallScore,
        DimensionScores scores,
        DeliveryMetrics delivery,
        List<String> improvementSuggestions,
        String sampleAnswer,
        List<Evidence> evidence) {

    public record DimensionScores(
            Double relevance,
            Double completeness,
            Double professionalism,
            Double starStructure,
            Double logic) { }

    public record DeliveryMetrics(
            Double speechRateCharactersPerMinute,
            Integer pauseCount,
            Double totalPauseSeconds,
            Integer fillerWordCount) { }

    /** Evidence is anchored to a playback offset so the report can jump into the video. */
    public record Evidence(String dimension, String quote, Double startSeconds, Double endSeconds) { }
}
