package com.guangge.Interview.practice.archive;

import com.guangge.Interview.analysis.EvaluationResult;
import com.guangge.Interview.practice.dto.PracticeQuestion;
import com.guangge.Interview.practice.entity.PracticeAnswerEntity;
import com.guangge.Interview.practice.entity.PracticeScoreEntity;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.repository.PracticeScoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * 成绩档案的写入与清理。
 *
 * 与 {@link PracticeArchiveService} 的分工：这个类只管"把一道题的分数存下来 / 删掉"，
 * 那边只管"把这些分数聚合成趋势与短板"。
 */
@Service
public class PracticeScoreArchiveService {

    private static final Logger log = LoggerFactory.getLogger(PracticeScoreArchiveService.class);

    /** 题干不长，这只是一道上限保险。 */
    private static final int MAX_QUESTION_TEXT = 2000;

    private final PracticeScoreRepository scores;

    public PracticeScoreArchiveService(PracticeScoreRepository scores) {
        this.scores = scores;
    }

    /**
     * 评分完成后记一笔。
     *
     * 幂等：同一场次的同一道题只存一条（库里还有唯一索引兜底），
     * 所以分析任务重试不会写出重复分数。
     */
    @Transactional
    public void record(PracticeSessionEntity session, PracticeAnswerEntity answer, PracticeQuestion question,
                       EvaluationResult result) {
        if (session == null || answer == null || result == null || result.scores() == null) return;

        // 转写修正后重评分会再次调用：已有记录要覆盖分数，而不是直接 return（否则档案永远停在旧分）。
        PracticeScoreEntity entity = scores
                .findFirstBySessionIdAndQuestionId(session.getId(), answer.getQuestionId())
                .orElseGet(() -> {
                    PracticeScoreEntity created = new PracticeScoreEntity();
                    created.setId(UUID.randomUUID());
                    return created;
                });
        entity.setOwnerId(session.getOwnerId());
        entity.setSessionId(session.getId());
        entity.setSessionCreatedAt(session.getCreatedAt() == null ? Instant.now() : session.getCreatedAt());
        entity.setQuestionIndex(answer.getQuestionIndex());
        entity.setQuestionId(answer.getQuestionId());
        // 题面留一份：场次 7 天后会被清理，错题本要靠它才能"把这道题重练一遍"（V10）
        if (question != null) {
            entity.setQuestionText(truncate(question.text(), MAX_QUESTION_TEXT));
            entity.setQuestionCategory(question.category());
        }
        entity.setRole(session.getRole());
        entity.setDifficulty(session.getDifficulty());
        entity.setInterviewType(session.getInterviewType());
        entity.setInterviewerStyle(session.getInterviewerStyle());
        entity.setOverallScore(scale(result.overallScore()));
        entity.setRelevance(scale(result.scores().relevance()));
        entity.setCompleteness(scale(result.scores().completeness()));
        entity.setProfessionalism(scale(result.scores().professionalism()));
        entity.setStarStructure(scale(result.scores().starStructure()));
        entity.setLogic(scale(result.scores().logic()));
        entity.setEvaluatedAt(Instant.now());
        scores.save(entity);
    }

    /** 兼容旧调用：没有题面信息时照样记分数（错题本里不会出现这道题）。 */
    @Transactional
    public void record(PracticeSessionEntity session, PracticeAnswerEntity answer, EvaluationResult result) {
        record(session, answer, null, result);
    }

    /** 题面按 text 列存，不需要无限长；超长只可能是模型失控，截断比报错好。 */
    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** 用户主动删掉一场练习时，这一场的成绩也跟着删（"删除"就该是删除）。 */
    @Transactional
    public void forgetSession(UUID sessionId) {
        scores.deleteAllBySessionId(sessionId);
        log.info("删除练习成绩档案：session={}", sessionId);
    }

    /** 一键清空档案：保留录像与练习本身，只清掉长期统计。 */
    @Transactional
    public int clear(String ownerId) {
        int removed = (int) scores.countByOwnerId(ownerId);
        scores.deleteAllByOwnerId(ownerId);
        log.info("清空训练档案：owner={}，删除 {} 条", ownerId, removed);
        return removed;
    }

    /** 分数统一两位小数：展示与平均都省得处理浮点尾巴。 */
    private static BigDecimal scale(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
