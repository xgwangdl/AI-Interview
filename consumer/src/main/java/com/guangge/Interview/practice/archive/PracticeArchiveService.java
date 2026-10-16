package com.guangge.Interview.practice.archive;

import com.guangge.Interview.practice.dto.PracticeArchiveView;
import com.guangge.Interview.practice.dto.PracticeReportView;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import com.guangge.Interview.practice.service.PracticeReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 训练档案：跨场次的成长与短板。
 *
 * 复用 {@link PracticeReportService} 的每场汇总（它已经算好了 5 个维度的平均分），
 * 这样"哪些维度、怎么平均"只有一处定义，不会两边算法漂移。
 */
@Service
public class PracticeArchiveService {

    public static final int DEFAULT_WINDOW = 5;
    /** 一次最多回看这么多场：再多也不代表"最近状态"，图表也放不下。 */
    public static final int MAX_WINDOW = 10;
    /** 低于这个分数才算短板 —— 全是 90 分的时候不该硬挑一个出来说"你这里差"。 */
    private static final double WEAK_THRESHOLD = 75.0;
    private static final int MAX_WEAK = 3;
    private static final String COMPLETED = "completed";

    private final PracticeSessionRepository sessions;
    private final PracticeReportService reports;

    public PracticeArchiveService(PracticeSessionRepository sessions, PracticeReportService reports) {
        this.sessions = sessions;
        this.reports = reports;
    }

    @Transactional(readOnly = true)
    public PracticeArchiveView summary(String ownerId, Integer limit) {
        int window = limit == null ? DEFAULT_WINDOW : Math.min(Math.max(limit, 1), MAX_WINDOW);
        List<PracticeSessionEntity> recent = sessions.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .filter(session -> COMPLETED.equals(session.getStatus()))
                .limit(window)
                .toList();

        List<PracticeArchiveView.SessionPoint> trend = new ArrayList<>();
        Map<String, List<Double>> byDimension = new LinkedHashMap<>();
        int evaluatedAnswers = 0;
        // recent 是"近 → 远"，倒过来遍历就是趋势图要的"旧 → 新"
        for (int index = recent.size() - 1; index >= 0; index--) {
            PracticeSessionEntity session = recent.get(index);
            PracticeReportView report = reports.report(ownerId, session.getId());
            PracticeReportView.ReportSummary summary = report.summary();
            // 还没评分的场次不进档案：0 分和"没分数"是两回事
            if (summary == null || summary.averageOverallScore() == null) continue;

            Map<String, Double> dimensions = summary.averageDimensionScores() == null
                    ? Map.of() : summary.averageDimensionScores();
            dimensions.forEach((key, value) -> {
                if (value != null) byDimension.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
            });
            evaluatedAnswers += report.evaluatedCount();
            trend.add(new PracticeArchiveView.SessionPoint(
                    session.getId(), session.getRole(), session.getCreatedAt(),
                    summary.averageOverallScore(), dimensions));
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
                "只统计还没过期的已完成练习（默认保留 7 天，更早的记录会随保留策略一起清掉）",
                window,
                new PracticeArchiveView.Totals(trend.size(), evaluatedAnswers),
                trend.size() >= 2,
                trend,
                dimensions,
                weakest);
    }

    private static double round(List<Double> values) {
        double raw = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return Math.round(raw * 100.0) / 100.0;
    }
}
