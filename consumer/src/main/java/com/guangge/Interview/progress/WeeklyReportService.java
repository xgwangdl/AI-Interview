package com.guangge.Interview.progress;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.analysis.EvaluationResult;
import com.guangge.Interview.analysis.EvaluationResultValidator;
import com.guangge.Interview.practice.entity.PracticeAnswerEntity;
import com.guangge.Interview.practice.entity.PracticeScoreEntity;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.repository.PracticeAnswerRepository;
import com.guangge.Interview.practice.repository.PracticeScoreRepository;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import com.guangge.Interview.practice.service.PracticeQuestionCatalog;
import com.guangge.Interview.goal.StudyPlanItemEntity;
import com.guangge.Interview.goal.StudyPlanItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WeeklyReportService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Map<String, String> LABELS = Map.of(
            "relevance", "内容相关性",
            "completeness", "回答完整度",
            "professionalism", "专业程度",
            "starStructure", "STAR 结构",
            "logic", "回答逻辑");
    private static final Map<String, String> ADVICE = Map.of(
            "relevance", "回答前先复述问题里的关键点，确认没有答偏。",
            "completeness", "按“结论 → 两三条理由 → 一个例子”把要点答全。",
            "professionalism", "多讲具体方案、参数和取舍，少说笼统的“优化了性能”。",
            "starStructure", "讲经历按 Situation → Task → Action → Result 走。",
            "logic", "先用一句话给结论，再分点展开。");

    private final PracticeSessionRepository sessions;
    private final PracticeAnswerRepository answers;
    private final PracticeScoreRepository scores;
    private final StudyPlanItemRepository planItems;
    private final PracticeQuestionCatalog questions;
    private final EvaluationResultValidator validator;
    private final WeeklyReportRepository reports;
    private final ObjectMapper objectMapper;

    public WeeklyReportService(PracticeSessionRepository sessions, PracticeAnswerRepository answers,
                               PracticeScoreRepository scores, StudyPlanItemRepository planItems,
                               PracticeQuestionCatalog questions, EvaluationResultValidator validator,
                               WeeklyReportRepository reports, ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.answers = answers;
        this.scores = scores;
        this.planItems = planItems;
        this.questions = questions;
        this.validator = validator;
        this.reports = reports;
        this.objectMapper = objectMapper;
    }

    /** 默认本周（周一到周日）；传日期则取该日期所在周。 */
    @Transactional
    public WeeklyReportView report(String ownerId, LocalDate anchor) {
        LocalDate weekStart = (anchor == null ? LocalDate.now(ZONE) : anchor)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        Instant from = weekStart.atStartOfDay(ZONE).toInstant();
        Instant to = weekEnd.plusDays(1).atStartOfDay(ZONE).toInstant();

        List<StudyPlanItemEntity> items = planItems
                .findAllByOwnerIdAndPlanDateBetweenOrderByPlanDateDescPositionAsc(ownerId, weekStart, weekEnd);
        int planCompleted = (int) items.stream().filter(item -> "completed".equals(item.getStatus())).count();

        // 只统计真正完成的练习：进行中/刚创建的空场次会把"场次"数字撑得没有意义
        List<PracticeSessionEntity> weekSessions = sessions.findAllByOwnerIdAndCreatedAtBetween(ownerId, from, to)
                .stream().filter(session -> "completed".equals(session.getStatus())).toList();
        List<UUID> sessionIds = weekSessions.stream().map(PracticeSessionEntity::getId).toList();
        int answerCount = sessionIds.isEmpty() ? 0 : (int) answers.countBySessionIdInAndIsCurrentTrue(sessionIds);
        List<PracticeScoreEntity> weekScores = scores.findAllByOwnerIdAndEvaluatedAtBetween(ownerId, from, to);
        Double average = average(weekScores);

        Instant previousFrom = weekStart.minusWeeks(1).atStartOfDay(ZONE).toInstant();
        Instant previousTo = weekStart.atStartOfDay(ZONE).toInstant();
        Double previous = average(scores.findAllByOwnerIdAndEvaluatedAtBetween(ownerId, previousFrom, previousTo));

        Map<String, Double> dimensions = new LinkedHashMap<>();
        dimensions.put("relevance", dimension(weekScores, PracticeScoreEntity::getRelevance));
        dimensions.put("completeness", dimension(weekScores, PracticeScoreEntity::getCompleteness));
        dimensions.put("professionalism", dimension(weekScores, PracticeScoreEntity::getProfessionalism));
        dimensions.put("starStructure", dimension(weekScores, PracticeScoreEntity::getStarStructure));
        dimensions.put("logic", dimension(weekScores, PracticeScoreEntity::getLogic));

        List<String> suggestions = dimensions.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() < 75)
                .sorted(Map.Entry.comparingByValue())
                .limit(3)
                .map(entry -> LABELS.getOrDefault(entry.getKey(), entry.getKey()) + "："
                        + ADVICE.getOrDefault(entry.getKey(), "下一周重点练这一项。"))
                .toList();
        if (suggestions.isEmpty()) {
            suggestions = List.of("本周没有明显短板。下一周可以换一个岗位，或把难度提高到 advanced。");
        }

        WeeklyReportView.BestImprovement best = bestImprovement(weekSessions);
        WeeklyReportView view = new WeeklyReportView(
                weekStart, weekEnd, items.size(), planCompleted,
                items.isEmpty() ? 0 : round(planCompleted * 100.0 / items.size()),
                weekSessions.size(), answerCount, average, previous,
                average == null || previous == null ? null : round(average - previous),
                dimensions, suggestions, best, Instant.now());
        persist(ownerId, view);
        return view;
    }

    private WeeklyReportView.BestImprovement bestImprovement(List<PracticeSessionEntity> weekSessions) {
        WeeklyReportView.BestImprovement best = null;
        double bestDelta = 0;
        for (PracticeSessionEntity session : weekSessions) {
            Map<Integer, List<PracticeAnswerEntity>> grouped = new LinkedHashMap<>();
            for (PracticeAnswerEntity answer : answers.findAllBySessionIdOrderByQuestionIndexAscAttemptAsc(
                    session.getId())) {
                grouped.computeIfAbsent(answer.getQuestionIndex(), ignored -> new ArrayList<>()).add(answer);
            }
            for (List<PracticeAnswerEntity> attempts : grouped.values()) {
                if (attempts.size() < 2) continue;
                EvaluationResult before = read(attempts.get(0));
                EvaluationResult after = read(attempts.get(attempts.size() - 1));
                if (before == null || after == null) continue;
                double delta = after.overallScore() - before.overallScore();
                if (delta > bestDelta) {
                    bestDelta = delta;
                    String question = attempts.get(0).getQuestionId().equals("") ? ""
                            : questions.questionText(session.getQuestionsJson(), attempts.get(0).getQuestionId());
                    best = new WeeklyReportView.BestImprovement(attempts.get(0).getQuestionIndex(), question,
                            before.overallScore(), after.overallScore(),
                            attempts.get(0).getTranscriptText(), attempts.get(attempts.size() - 1).getTranscriptText());
                }
            }
        }
        return best;
    }

    private EvaluationResult read(PracticeAnswerEntity answer) {
        if (answer.getEvaluationJson() == null || answer.getEvaluationJson().isBlank()) return null;
        try {
            return validator.parseReport(answer.getEvaluationJson()).result();
        } catch (Exception exception) {
            return null;
        }
    }

    private void persist(String ownerId, WeeklyReportView view) {
        WeeklyReportEntity entity = reports.findFirstByOwnerIdAndWeekStart(ownerId, view.weekStart())
                .orElseGet(WeeklyReportEntity::new);
        if (entity.getId() == null) entity.setId(UUID.randomUUID());
        entity.setOwnerId(ownerId);
        entity.setWeekStart(view.weekStart());
        entity.setWeekEnd(view.weekEnd());
        entity.setGeneratedAt(view.generatedAt());
        try {
            entity.setReportJson(objectMapper.writeValueAsString(view));
        } catch (Exception exception) {
            throw new IllegalStateException("周报序列化失败", exception);
        }
        reports.save(entity);
    }

    private static Double average(List<PracticeScoreEntity> rows) {
        java.util.OptionalDouble value = rows.stream()
                .map(PracticeScoreEntity::getOverallScore).filter(java.util.Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue).average();
        return value.isPresent() ? round(value.getAsDouble()) : null;
    }

    private static Double dimension(List<PracticeScoreEntity> rows,
                                    java.util.function.Function<PracticeScoreEntity, BigDecimal> pick) {
        java.util.OptionalDouble value = rows.stream().map(pick).filter(java.util.Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue).average();
        return value.isPresent() ? round(value.getAsDouble()) : null;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
