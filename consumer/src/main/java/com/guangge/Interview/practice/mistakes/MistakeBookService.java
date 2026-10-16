package com.guangge.Interview.practice.mistakes;

import com.guangge.Interview.practice.dto.PracticeQuestion;
import com.guangge.Interview.practice.dto.PracticeSessionRequest;
import com.guangge.Interview.practice.dto.PracticeSessionView;
import com.guangge.Interview.practice.entity.PracticeScoreEntity;
import com.guangge.Interview.practice.repository.PracticeScoreRepository;
import com.guangge.Interview.practice.service.PracticeQuestionCatalog;
import com.guangge.Interview.practice.service.PracticeSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 错题本：一道题一道题地练，直到过线。
 *
 * 数据源是成绩档案（V10 起每条记录都带题面），所以**场次过了保留期也能重练** ——
 * 题面如果只存在 practice_sessions 里，7 天后错题本就成了没有题干的名字列表。
 *
 * "错题"的定义：同一道题的所有记录里**最好的一次**都没到 75 分。
 * 因此重练过线后它会自动消失，不需要额外的"学会了"按钮。
 */
@Service
public class MistakeBookService {

    /** 与训练档案的短板阈值一致：两处都说 75，用户才不会觉得两套标准打架。 */
    public static final double PASS_THRESHOLD = 75.0;
    static final int DEFAULT_LIMIT = 5;
    static final int MAX_LIMIT = 20;
    /** 单题重练只录一段回答，不用 20 分钟；跟前端"每题 4 分钟"的估算保持一致。 */
    static final int RETRY_DURATION_MINUTES = 4;
    private static final String SCOPE_NOTE =
            "只收最好一次也没到 %.0f 分的题（按题目去重，重练同一道题算同一道）；追问不算错题，重练原题就够";
    private static final Map<String, Function<PracticeScoreEntity, BigDecimal>> DIMENSIONS = new LinkedHashMap<>();

    static {
        DIMENSIONS.put("relevance", PracticeScoreEntity::getRelevance);
        DIMENSIONS.put("completeness", PracticeScoreEntity::getCompleteness);
        DIMENSIONS.put("professionalism", PracticeScoreEntity::getProfessionalism);
        DIMENSIONS.put("starStructure", PracticeScoreEntity::getStarStructure);
        DIMENSIONS.put("logic", PracticeScoreEntity::getLogic);
    }

    private final PracticeScoreRepository scores;
    private final PracticeSessionService sessions;

    public MistakeBookService(PracticeScoreRepository scores, PracticeSessionService sessions) {
        this.scores = scores;
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public MistakeBookView summary(String ownerId, Integer limit) {
        int capped = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);

        Map<String, List<PracticeScoreEntity>> byQuestion = new LinkedHashMap<>();
        for (PracticeScoreEntity row : scores.findScoredQuestions(ownerId)) {
            // 追问是同一道题的延伸：让用户重练它没有意义，原题练好才是目标
            if (PracticeQuestionCatalog.FOLLOW_UP_CATEGORY.equals(row.getQuestionCategory())) continue;
            byQuestion.computeIfAbsent(row.getQuestionId(), ignored -> new ArrayList<>()).add(row);
        }

        List<MistakeBookView.Mistake> mistakes = byQuestion.values().stream()
                .map(MistakeBookService::toMistake)
                .filter(mistake -> mistake.bestScore() != null && mistake.bestScore() < PASS_THRESHOLD)
                .sorted(Comparator.comparingDouble(MistakeBookView.Mistake::bestScore)
                        // 分数一样时先看最近栽的那道题，用户更有印象
                        .thenComparing(MistakeBookView.Mistake::latestAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return new MistakeBookView(SCOPE_NOTE.formatted(PASS_THRESHOLD), PASS_THRESHOLD,
                mistakes.size(), mistakes.stream().limit(capped).toList());
    }

    /**
     * 重练这道题：直接建一场只有这一道题的练习，题面就是原来那道题。
     *
     * 不走 AI 出题 —— 用户要练的是这道题，不是"这个岗位的题"；顺带也省一次模型调用。
     */
    @Transactional
    public PracticeSessionView retry(String ownerId, String questionId) {
        PracticeScoreEntity row = scores
                .findFirstByOwnerIdAndQuestionIdAndQuestionTextIsNotNullOrderByEvaluatedAtDesc(ownerId, questionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "错题本里没有这道题"));
        PracticeQuestion question = new PracticeQuestion(row.getQuestionId(), row.getQuestionText(),
                row.getQuestionCategory() == null || row.getQuestionCategory().isBlank()
                        ? "错题重练" : row.getQuestionCategory());
        PracticeSessionRequest request = new PracticeSessionRequest(
                blankTo(row.getRole(), "面试练习"),
                null,
                null,
                null,
                blankTo(row.getInterviewType(), "behavioral"),
                blankTo(row.getDifficulty(), "standard"),
                blankTo(row.getInterviewerStyle(), "balanced"),
                1,
                RETRY_DURATION_MINUTES,
                // 重练一道题，追问正合适：就这一个话题往下挖，比再问十道新题有用
                true,
                List.of(question));
        return sessions.create(ownerId, request);
    }

    private static MistakeBookView.Mistake toMistake(List<PracticeScoreEntity> rows) {
        // 查询按评分时间倒序：第一条就是最近一次
        PracticeScoreEntity latest = rows.get(0);
        java.util.OptionalDouble best = rows.stream()
                .map(PracticeScoreEntity::getOverallScore)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .max();
        Map.Entry<String, Double> weakest = weakestDimension(rows);

        return new MistakeBookView.Mistake(
                latest.getQuestionId(),
                latest.getQuestionText(),
                latest.getQuestionCategory(),
                latest.getRole(),
                latest.getDifficulty(),
                latest.getInterviewType(),
                latest.getInterviewerStyle(),
                rows.size(),
                best.isPresent() ? best.getAsDouble() : null,
                score(latest.getOverallScore()),
                latest.getEvaluatedAt(),
                weakest == null ? null : weakest.getKey(),
                weakest == null ? null : weakest.getValue());
    }

    /** 这道题反复栽在哪个维度上：各维度取平均后最小的那个（都没分就给 null）。 */
    private static Map.Entry<String, Double> weakestDimension(List<PracticeScoreEntity> rows) {
        Map.Entry<String, Double> weakest = null;
        for (Map.Entry<String, Function<PracticeScoreEntity, BigDecimal>> dimension : DIMENSIONS.entrySet()) {
            double average = rows.stream()
                    .map(dimension.getValue())
                    .filter(java.util.Objects::nonNull)
                    .mapToDouble(BigDecimal::doubleValue)
                    .average().orElse(Double.NaN);
            if (Double.isNaN(average)) continue;
            if (weakest == null || average < weakest.getValue()) {
                weakest = Map.entry(dimension.getKey(), Math.round(average * 10) / 10.0);
            }
        }
        return weakest;
    }

    private static Double score(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
