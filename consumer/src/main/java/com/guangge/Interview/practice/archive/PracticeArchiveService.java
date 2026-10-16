package com.guangge.Interview.practice.archive;

import com.guangge.Interview.practice.dto.PracticeArchiveView;
import com.guangge.Interview.practice.entity.PracticeScoreEntity;
import com.guangge.Interview.practice.repository.PracticeScoreRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 训练档案：跨场次的成长与短板。
 *
 * 数据源是 `practice_score_archive`（V9 起与录像/转写分开保留），所以**练习过期后档案仍在** ——
 * 这也是这个功能从"7 天视野"变成"长期视野"的地方。
 */
@Service
public class PracticeArchiveService {

    public static final int DEFAULT_WINDOW = 5;
    /** 一次最多回看这么多场：再多也不代表"最近状态"，图表也放不下。 */
    public static final int MAX_WINDOW = 10;
    /** 低于这个分数才算短板 —— 全是 90 分的时候不该硬挑一个出来说"你这里差"。 */
    private static final double WEAK_THRESHOLD = 75.0;
    private static final int MAX_WEAK = 3;
    private static final String SCOPE_NOTE =
            "统计长期保留的分数（不含录像与转写；录像仍按 7 天清理），最近 %d 场练习";

    private final PracticeScoreRepository scores;

    public PracticeArchiveService(PracticeScoreRepository scores) {
        this.scores = scores;
    }

    @Transactional(readOnly = true)
    public PracticeArchiveView summary(String ownerId, Integer limit) {
        int window = limit == null ? DEFAULT_WINDOW : Math.min(Math.max(limit, 1), MAX_WINDOW);
        List<UUID> sessionIds = scores.findRecentSessionIds(ownerId, PageRequest.of(0, window));
        if (sessionIds.isEmpty()) {
            return empty(window);
        }

        Map<UUID, List<PracticeScoreEntity>> bySession = new LinkedHashMap<>();
        for (PracticeScoreEntity score : scores.findAllByOwnerIdAndSessionIdIn(ownerId, sessionIds)) {
            bySession.computeIfAbsent(score.getSessionId(), ignored -> new ArrayList<>()).add(score);
        }

        // 趋势要"旧 → 新"：按场次创建时间升序
        List<List<PracticeScoreEntity>> ordered = bySession.values().stream()
                .sorted(Comparator.comparing(PracticeArchiveService::createdAt))
                .toList();

        List<PracticeArchiveView.SessionPoint> trend = new ArrayList<>();
        Map<String, List<Double>> byDimension = new LinkedHashMap<>();
        int evaluatedAnswers = 0;
        for (List<PracticeScoreEntity> answers : ordered) {
            Map<String, Double> dimensions = new LinkedHashMap<>();
            putIfPresent(dimensions, "relevance", average(answers, PracticeScoreEntity::getRelevance));
            putIfPresent(dimensions, "completeness", average(answers, PracticeScoreEntity::getCompleteness));
            putIfPresent(dimensions, "professionalism", average(answers, PracticeScoreEntity::getProfessionalism));
            putIfPresent(dimensions, "starStructure", average(answers, PracticeScoreEntity::getStarStructure));
            putIfPresent(dimensions, "logic", average(answers, PracticeScoreEntity::getLogic));

            Double overall = average(answers, PracticeScoreEntity::getOverallScore);
            // 场次里只要有一条有分数就进档案；"0 分"和"没评分"在这里已经天然分开
            if (overall == null) continue;

            dimensions.forEach((key, value) ->
                    byDimension.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value));
            evaluatedAnswers += (int) answers.stream().filter(row -> row.getOverallScore() != null).count();
            PracticeScoreEntity head = answers.get(0);
            trend.add(new PracticeArchiveView.SessionPoint(
                    head.getSessionId(), head.getRole(), head.getSessionCreatedAt(), overall, dimensions));
        }

        List<PracticeArchiveView.DimensionStat> dimensions = byDimension.entrySet().stream()
                .map(entry -> new PracticeArchiveView.DimensionStat(
                        entry.getKey(), round(entry.getValue()), entry.getValue().size()))
                .toList();
        List<PracticeArchiveView.DimensionStat> weakest = dimensions.stream()
                .filter(stat -> stat.average() < WEAK_THRESHOLD)
                .sorted(Comparator.comparingDouble(PracticeArchiveView.DimensionStat::average))
                .limit(MAX_WEAK)
                .toList();

        return new PracticeArchiveView(
                SCOPE_NOTE.formatted(ordered.size()),
                window,
                new PracticeArchiveView.Totals(trend.size(), evaluatedAnswers),
                trend.size() >= 2,
                trend,
                dimensions,
                weakest);
    }

    private static PracticeArchiveView empty(int window) {
        return new PracticeArchiveView(SCOPE_NOTE.formatted(0), window,
                new PracticeArchiveView.Totals(0, 0), false, List.of(), List.of(), List.of());
    }

    private static Instant createdAt(List<PracticeScoreEntity> answers) {
        Instant value = answers.get(0).getSessionCreatedAt();
        return value == null ? Instant.EPOCH : value;
    }

    private static void putIfPresent(Map<String, Double> sink, String key, Double value) {
        if (value != null) sink.put(key, value);
    }

    private static Double average(List<PracticeScoreEntity> rows, java.util.function.Function<PracticeScoreEntity, BigDecimal> pick) {
        double raw = rows.stream()
                .map(pick)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(Double.NaN);
        return Double.isNaN(raw) ? null : round(raw);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double round(List<Double> values) {
        return round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }
}
