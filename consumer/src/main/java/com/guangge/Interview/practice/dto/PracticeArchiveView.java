package com.guangge.Interview.practice.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 训练档案：把一场一场的练习成绩拼成"最近状态 + 短板"（见 docs/PRACTICE-ARCHIVE-REQUIREMENTS.md）。
 *
 * 只做聚合，不引入新表：数据源就是还没过期的已完成练习及其评分
 * —— 保留策略仍是"7 天后连录像带记录一起清"，所以这里明确把范围写在 {@code scopeNote} 里，
 * 不假装自己在做长期跟踪（长期保留是第二步的事）。
 */
public record PracticeArchiveView(
        String scopeNote,
        int windowSize,
        Totals totals,
        boolean enoughForTrend,
        List<SessionPoint> trend,
        List<DimensionStat> dimensions,
        List<DimensionStat> weakest) {

    public record Totals(int scoredSessions, int evaluatedAnswers) { }

    /** 一场已完成练习的成绩点；{@code trend} 按时间正序（旧 → 新）。 */
    public record SessionPoint(
            UUID sessionId,
            String role,
            Instant createdAt,
            Double overall,
            Map<String, Double> dimensions) { }

    /** 某个维度在窗口内的平均分与样本数（samples = 贡献了这个分数的场次数）。 */
    public record DimensionStat(String key, double average, int samples) { }
}
