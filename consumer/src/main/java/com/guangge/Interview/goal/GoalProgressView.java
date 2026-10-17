package com.guangge.Interview.goal;

import java.time.Instant;
import java.util.Map;

/**
 * 目标进度：首页与目标详情共用。
 *
 * 这里的平均分只统计"挂了该目标"的练习；快速练习不计入，避免把一个岗位的进度
 * 和无关练习混在一起。
 */
public record GoalProgressView(
        int sessions,
        int answers,
        Double averageScore,
        Map<String, Double> dimensions,
        int planItems,
        int planCompleted,
        Instant lastPracticedAt) {

    public static GoalProgressView empty() {
        return new GoalProgressView(0, 0, null, Map.of(), 0, 0, null);
    }
}
