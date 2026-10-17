package com.guangge.Interview.progress;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.analysis.EvaluationReport;
import com.guangge.Interview.analysis.EvaluationResult;
import com.guangge.Interview.analysis.EvaluationResultValidator;
import com.guangge.Interview.goal.StudyPlanItemEntity;
import com.guangge.Interview.goal.StudyPlanItemRepository;
import com.guangge.Interview.practice.entity.PracticeAnswerEntity;
import com.guangge.Interview.practice.entity.PracticeScoreEntity;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.repository.PracticeAnswerRepository;
import com.guangge.Interview.practice.repository.PracticeScoreRepository;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import com.guangge.Interview.practice.service.PracticeQuestionCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeeklyReportServiceTest {

    private final PracticeSessionRepository sessions = mock(PracticeSessionRepository.class);
    private final PracticeAnswerRepository answers = mock(PracticeAnswerRepository.class);
    private final PracticeScoreRepository scores = mock(PracticeScoreRepository.class);
    private final StudyPlanItemRepository planItems = mock(StudyPlanItemRepository.class);
    private final WeeklyReportRepository reports = mock(WeeklyReportRepository.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final EvaluationResultValidator validator = new EvaluationResultValidator(mapper);
    private final WeeklyReportService service = new WeeklyReportService(sessions, answers, scores, planItems,
            new PracticeQuestionCatalog(mapper), validator, reports, mapper);

    @BeforeEach
    void setUp() {
        when(reports.findFirstByOwnerIdAndWeekStart(any(), any())).thenReturn(Optional.empty());
        when(reports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("周报聚合计划完成率、场次、维度与 before/after")
    void aggregatesWeek() {
        LocalDate weekStart = LocalDate.of(2026, 10, 12);
        Instant now = Instant.now();
        PracticeSessionEntity session = new PracticeSessionEntity();
        session.setId(UUID.randomUUID());
        session.setOwnerId("smoke");
        session.setRole("Java 后端");
        session.setStatus("completed");
        session.setQuestionsJson("[{\"id\":\"q-1\",\"text\":\"讲一个项目\",\"category\":\"项目\"}]");
        when(planItems.findAllByOwnerIdAndPlanDateBetweenOrderByPlanDateDescPositionAsc(
                eq("smoke"), any(), any())).thenReturn(List.of(item("completed"), item("completed"),
                item("pending"), item("skipped")));
        when(sessions.findAllByOwnerIdAndCreatedAtBetween(eq("smoke"), any(), any())).thenReturn(List.of(session));
        when(answers.countBySessionIdInAndIsCurrentTrue(any())).thenReturn(2L);
        when(scores.findAllByOwnerIdAndEvaluatedAtBetween(eq("smoke"), any(), any()))
                .thenReturn(List.of(score(70, 80, 60, 75, 65, 70), score(74, 82, 64, 78, 70, 72)))
                .thenReturn(List.of(score(60, 60, 60, 60, 60, 60)));
        when(answers.findAllBySessionIdOrderByQuestionIndexAscAttemptAsc(session.getId()))
                .thenReturn(List.of(answer(1, 55), answer(2, 72)));

        WeeklyReportView report = service.report("smoke", weekStart);

        assertThat(report.planCompleted()).isEqualTo(2);
        assertThat(report.completionRate()).isEqualTo(50);
        assertThat(report.sessions()).isEqualTo(1);
        assertThat(report.answers()).isEqualTo(2);
        assertThat(report.averageScore()).isEqualTo(72);
        assertThat(report.dimensions().get("relevance")).isEqualTo(81);
        assertThat(report.scoreDelta()).isEqualTo(12);
        assertThat(report.suggestions()).isNotEmpty();
        assertThat(report.bestImprovement().beforeScore()).isEqualTo(55);
        assertThat(report.bestImprovement().afterScore()).isEqualTo(72);
    }

    private static StudyPlanItemEntity item(String status) {
        StudyPlanItemEntity item = new StudyPlanItemEntity();
        item.setStatus(status);
        return item;
    }

    private static PracticeScoreEntity score(double overall, double relevance, double completeness,
                                             double professionalism, double star, double logic) {
        PracticeScoreEntity row = new PracticeScoreEntity();
        row.setOverallScore(BigDecimal.valueOf(overall));
        row.setRelevance(BigDecimal.valueOf(relevance));
        row.setCompleteness(BigDecimal.valueOf(completeness));
        row.setProfessionalism(BigDecimal.valueOf(professionalism));
        row.setStarStructure(BigDecimal.valueOf(star));
        row.setLogic(BigDecimal.valueOf(logic));
        return row;
    }

    private PracticeAnswerEntity answer(int attempt, double overall) {
        PracticeAnswerEntity answer = new PracticeAnswerEntity();
        answer.setId(UUID.randomUUID());
        answer.setQuestionId("q-1");
        answer.setQuestionIndex(0);
        answer.setAttempt(attempt);
        answer.setCurrent(attempt == 2);
        answer.setTranscriptText(attempt == 2 ? "新版回答" : "旧版回答");
        EvaluationResult result = new EvaluationResult(overall,
                new EvaluationResult.DimensionScores(overall, overall, overall, overall, overall),
                new EvaluationResult.DeliveryMetrics(200.0, 1, 1.0, 2),
                List.of("补细节"), "示例回答", List.of());
        try {
            answer.setEvaluationJson(validator.write(new EvaluationReport("qwen", "qwen", "v1",
                    Instant.now(), "", result)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        return answer;
    }
}
