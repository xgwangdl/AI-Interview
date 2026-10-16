package com.guangge.Interview.practice.archive;

import com.guangge.Interview.analysis.EvaluationResult;
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
    public void record(PracticeSessionEntity session, PracticeAnswerEntity answer, EvaluationResult result) {
        if (session == null || answer == null || result == null || result.scores() == null) return;
        if (scores.existsBySessionIdAndQuestionId(session.getId(), answer.getQuestionId())) return;

        PracticeScoreEntity entity = new PracticeScoreEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerId(session.getOwnerId());
        entity.setSessionId(session.getId());
        entity.setSessionCreatedAt(session.getCreatedAt() == null ? Instant.now() : session.getCreatedAt());
        entity.setQuestionIndex(answer.getQuestionIndex());
        entity.setQuestionId(answer.getQuestionId());
        entity.setRole(session.getRole());
        entity.setDifficulty(session.getDifficulty());
        entity.setOverallScore(scale(result.overallScore()));
        entity.setRelevance(scale(result.scores().relevance()));
        entity.setCompleteness(scale(result.scores().completeness()));
        entity.setProfessionalism(scale(result.scores().professionalism()));
        entity.setStarStructure(scale(result.scores().starStructure()));
        entity.setLogic(scale(result.scores().logic()));
        entity.setEvaluatedAt(Instant.now());
        scores.save(entity);
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
