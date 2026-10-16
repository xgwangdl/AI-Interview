package com.guangge.Interview.practice.archive;

import com.guangge.Interview.analysis.EvaluationResult;
import com.guangge.Interview.practice.dto.PracticeQuestion;
import com.guangge.Interview.practice.entity.PracticeAnswerEntity;
import com.guangge.Interview.practice.entity.PracticeScoreEntity;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.repository.PracticeScoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 成绩档案的写入与清理：幂等、只存分数、删除时一起走。 */
class PracticeScoreArchiveServiceTest {

    private final PracticeScoreRepository scores = mock(PracticeScoreRepository.class);
    private final PracticeScoreArchiveService service = new PracticeScoreArchiveService(scores);

    @Test
    @DisplayName("评分完成后落一条成绩档案，只存分数与维度")
    void recordsOneRowPerAnswer() {
        PracticeSessionEntity session = session();
        PracticeAnswerEntity answer = answer(session.getId());
        when(scores.existsBySessionIdAndQuestionId(session.getId(), "q-2")).thenReturn(false);

        service.record(session, answer, evaluation());

        ArgumentCaptor<PracticeScoreEntity> captor = ArgumentCaptor.forClass(PracticeScoreEntity.class);
        verify(scores).save(captor.capture());
        PracticeScoreEntity saved = captor.getValue();
        assertThat(saved.getOwnerId()).isEqualTo("smoke");
        assertThat(saved.getSessionId()).isEqualTo(session.getId());
        assertThat(saved.getQuestionId()).isEqualTo("q-2");
        assertThat(saved.getRole()).isEqualTo("Java 后端工程师");
        assertThat(saved.getOverallScore()).isEqualByComparingTo("78.5");
        assertThat(saved.getStarStructure()).isEqualByComparingTo("61.0");
        assertThat(saved.getSessionCreatedAt()).isEqualTo(session.getCreatedAt());
    }

    @Test
    @DisplayName("同一场次的同一道题只写一次：分析任务重试不会写出重复分数")
    void isIdempotent() {
        PracticeSessionEntity session = session();
        PracticeAnswerEntity answer = answer(session.getId());
        when(scores.existsBySessionIdAndQuestionId(session.getId(), "q-2")).thenReturn(true);

        service.record(session, answer, evaluation());

        verify(scores, never()).save(any());
    }

    @Test
    @DisplayName("带上题面一起存：错题本要靠它才能在录像清掉之后重练那道题")
    void keepsTheQuestionText() {
        PracticeSessionEntity session = session();
        PracticeAnswerEntity answer = answer(session.getId());
        when(scores.existsBySessionIdAndQuestionId(session.getId(), "q-2")).thenReturn(false);

        service.record(session, answer, new PracticeQuestion("q-2", "讲一个你优化过的接口", "technical"),
                evaluation());

        ArgumentCaptor<PracticeScoreEntity> captor = ArgumentCaptor.forClass(PracticeScoreEntity.class);
        verify(scores).save(captor.capture());
        assertThat(captor.getValue().getQuestionText()).isEqualTo("讲一个你优化过的接口");
        assertThat(captor.getValue().getQuestionCategory()).isEqualTo("technical");
    }

    @Test
    @DisplayName("分数不完整时也能存：缺的维度留空，而不是塞 0")
    void toleratesMissingDimensions() {
        PracticeSessionEntity session = session();
        PracticeAnswerEntity answer = answer(session.getId());
        EvaluationResult partial = new EvaluationResult(70.0,
                new EvaluationResult.DimensionScores(70.0, null, 70.0, null, 70.0),
                new EvaluationResult.DeliveryMetrics(200.0, 1, 2.0, 3),
                List.of(), "sample", List.of());
        when(scores.existsBySessionIdAndQuestionId(session.getId(), "q-2")).thenReturn(false);

        service.record(session, answer, partial);

        ArgumentCaptor<PracticeScoreEntity> captor = ArgumentCaptor.forClass(PracticeScoreEntity.class);
        verify(scores).save(captor.capture());
        assertThat(captor.getValue().getCompleteness()).isNull();
        assertThat(captor.getValue().getRelevance()).isEqualByComparingTo("70.0");
    }

    @Test
    @DisplayName("删除一场练习时成绩一起删；清空档案只删自己的")
    void forgetsAndClears() {
        UUID sessionId = UUID.randomUUID();
        service.forgetSession(sessionId);
        verify(scores).deleteAllBySessionId(sessionId);

        when(scores.countByOwnerId("smoke")).thenReturn(7L);
        assertThat(service.clear("smoke")).isEqualTo(7);
        verify(scores).deleteAllByOwnerId("smoke");
    }

    private static PracticeSessionEntity session() {
        PracticeSessionEntity entity = new PracticeSessionEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerId("smoke");
        entity.setRole("Java 后端工程师");
        entity.setDifficulty("standard");
        entity.setCreatedAt(Instant.now().minusSeconds(3600));
        entity.setStatus("completed");
        return entity;
    }

    private static PracticeAnswerEntity answer(UUID sessionId) {
        PracticeAnswerEntity entity = new PracticeAnswerEntity();
        entity.setId(UUID.randomUUID());
        entity.setSessionId(sessionId);
        entity.setQuestionIndex(2);
        entity.setQuestionId("q-2");
        return entity;
    }

    private static EvaluationResult evaluation() {
        return new EvaluationResult(78.5,
                new EvaluationResult.DimensionScores(82.0, 74.0, 80.0, 61.0, 70.0),
                new EvaluationResult.DeliveryMetrics(230.0, 2, 3.5, 4),
                List.of("补一条量化结果"), "sample", List.of());
    }
}
