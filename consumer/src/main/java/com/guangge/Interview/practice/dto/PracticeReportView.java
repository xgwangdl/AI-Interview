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
            /** 其中有多少个回答其实是面试官的追问（这些题也要说清楚，不然报告里会平白多出几道题）。 */
            int followUpCount,
            List<String> improvementSuggestions) { }

    /**
     * 一道题的报告。
     *
     * {@code followUp} 为真时，这道题是面试官根据上一题的回答追问出来的：
     * 前端把它挂在 {@code parentQuestionIndex} 那道题下面，而不是当成独立的一题。
     */
    public record QuestionReport(
            UUID answerId,
            int questionIndex,
            String questionId,
            String questionText,
            Integer durationSeconds,
            String transcriptStatus,
            String transcriptText,
            String transcriptOriginal,
            Instant transcriptEditedAt,
            String evaluationStatus,
            String mediaUrl,
            String mediaSignedUrl,
            boolean followUp,
            String parentQuestionId,
            Integer parentQuestionIndex,
            int followUpRound,
            EvaluationResult evaluation) { }
}
