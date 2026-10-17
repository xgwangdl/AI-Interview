package com.guangge.Interview.goal;

import com.guangge.Interview.practice.repository.PracticeScoreRepository;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import com.guangge.Interview.practice.repository.PracticeAnswerRepository;
import com.guangge.Interview.practice.service.PracticeSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 求职目标：创建、倒计时、越权、删除语义。 */
class JobGoalServiceTest {

    private final JobGoalRepository goals = mock(JobGoalRepository.class);
    private final StudyPlanRepository plans = mock(StudyPlanRepository.class);
    private final StudyPlanItemRepository planItems = mock(StudyPlanItemRepository.class);
    private final PracticeSessionRepository sessions = mock(PracticeSessionRepository.class);
    private final PracticeScoreRepository scores = mock(PracticeScoreRepository.class);
    private final PracticeAnswerRepository answers = mock(PracticeAnswerRepository.class);
    private final PracticeSessionService practiceSessions = mock(PracticeSessionService.class);
    private final JobGoalService service = new JobGoalService(goals, plans, planItems, sessions, scores, answers,
            practiceSessions);

    @BeforeEach
    void defaults() {
        when(goals.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(plans.findAllByOwnerIdAndGoalIdOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(sessions.findAllByOwnerIdAndGoalIdOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(scores.findAllByOwnerIdAndSessionIdIn(any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("创建目标：默认技术面/准备中，并按面试日期算天数")
    void createsGoalWithDefaultsAndCountdown() {
        Instant interviewAt = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(4)
                .atTime(14, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant();

        JobGoalView view = service.create("smoke", new JobGoalRequest(
                "字节跳动", "Java 后端", "负责订单系统", null, null, interviewAt, null, null));

        assertThat(view.company()).isEqualTo("字节跳动");
        assertThat(view.role()).isEqualTo("Java 后端");
        assertThat(view.stage()).isEqualTo("technical");
        assertThat(view.status()).isEqualTo("preparing");
        assertThat(view.daysUntilInterview()).isEqualTo(4);
        assertThat(view.progress().sessions()).isZero();
    }

    @Test
    @DisplayName("未知轮次/状态直接 400，不写脏数据")
    void rejectsUnknownStageAndStatus() {
        assertThatThrownBy(() -> service.create("smoke", new JobGoalRequest(
                null, "Java 后端", null, null, "nope", null, null, null)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> service.create("smoke", new JobGoalRequest(
                null, "Java 后端", null, null, "technical", null, "nope", null)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    @DisplayName("别人的目标一律 404")
    void hidesForeignGoals() {
        UUID id = UUID.randomUUID();
        when(goals.findByIdAndOwnerId(id, "smoke")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("smoke", id))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("目标不存在");
    }

    @Test
    @DisplayName("删除目标：练习解绑，计划级联删除")
    void deleteDetachesSessionsAndPlans() {
        JobGoalEntity goal = goal();
        when(goals.findByIdAndOwnerId(goal.getId(), "smoke")).thenReturn(Optional.of(goal));

        service.delete("smoke", goal.getId());

        verify(sessions).detachGoal(goal.getId());
        verify(plans).deleteAllByGoalId(goal.getId());
        verify(goals).delete(goal);
    }

    private static JobGoalEntity goal() {
        JobGoalEntity entity = new JobGoalEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerId("smoke");
        entity.setRole("Java 后端");
        entity.setStage("technical");
        entity.setStatus("preparing");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }
}
