package com.guangge.Interview.practice.dto;

import com.guangge.Interview.analysis.EvaluationResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PracticeReportView(
        UUID sessionId,
        String role,
        String status,
        int questionCount,
        int evaluatedCount,
        Instant generatedAt,
        ReportSummary summary,
        AnalysisState analysis,
        List<QuestionReport> questions) {

    /**
     * 转写与评分任务的整体状态。没有它，分析失败时前端只能一直显示「等待分析服务」。
     */
    public record AnalysisState(String status, int attempts, String error) { }

    public record ReportSummary(
            Double averageOverallScore,
            Map<String, Double> averageDimensionScores,
            double speechRateCharactersPerMinute,
            int totalPauseCount,
            double totalPauseSeconds,
            int totalFillerWordCount,
            List<String> improvementSuggestions) { }

    public record QuestionReport(
            UUID answerId,
            int questionIndex,
            String questionId,
            String questionText,
            Integer durationSeconds,
            String transcriptStatus,
            String transcriptText,
            String evaluationStatus,
            String mediaUrl,
            String mediaSignedUrl,
            EvaluationResult evaluation) { }
}
