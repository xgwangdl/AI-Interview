package com.guangge.Interview.progress;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 一周的训练复盘。
 *
 * 计划完成率、练习场次、维度变化与 before/after 都来自同一周的原始数据；
 * 不编"综合周分"，避免制造一个无法解释的新指标。
 */
public record WeeklyReportView(
        LocalDate weekStart,
        LocalDate weekEnd,
        int planTotal,
        int planCompleted,
        double completionRate,
        int sessions,
        int answers,
        Double averageScore,
        Double previousAverageScore,
        Double scoreDelta,
        Map<String, Double> dimensions,
        List<String> suggestions,
        BestImprovement bestImprovement,
        Instant generatedAt) {

    public record BestImprovement(int questionIndex, String questionText, Double beforeScore,
                                  Double afterScore, String beforeTranscript, String afterTranscript) { }
}
