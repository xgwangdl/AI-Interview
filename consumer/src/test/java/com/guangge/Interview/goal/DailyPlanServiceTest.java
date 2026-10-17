package com.guangge.Interview.goal;

import com.guangge.Interview.practice.archive.PracticeArchiveService;
import com.guangge.Interview.practice.dto.PracticeQuestion;
import com.guangge.Interview.practice.dto.PracticeSessionView;
import com.guangge.Interview.practice.mistakes.MistakeBookService;
import com.guangge.Interview.practice.mistakes.MistakeBookView;
import com.guangge.Interview.practice.service.PracticeSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 每日三题：规则、开始练习、连续记录。 */
class DailyPlanServiceTest {

    private final JobGoalService goals = mock(JobGoalService.class);
    private final StudyPlanRepository plans = mock(StudyPlanRepository.class);
    private final StudyPlanItemRepository items = mock(StudyPlanItemRepository.class);
    private final PracticeSessionService sessions = mock(PracticeSessionService.class);
    private final PracticeArchiveService archive = mock(PracticeArchiveService.class);
    private final MistakeBookService mistakes = mock(MistakeBookService.class);
    private final DailyPlanService service = new DailyPlanService(goals, plans, items, sessions, archive, mistakes);

    private JobGoalEntity goal;

    @BeforeEach
    void setUp() {
        goal = new JobGoalEntity();
        goal.setId(UUID.randomUUID());
        goal.setOwnerId("smoke");
        goal.setCompany("Light");
        goal.setRole("Java 后端");
        goal.setStage("technical");
        goal.setStatus("preparing");
        when(goals.preferredGoal("smoke")).thenReturn(goal);
        when(goals.requireOwned("smoke", goal.getId())).thenReturn(goal);
        when(plans.findAllByOwnerIdAndStatus("smoke", "active")).thenReturn(List.of());
        when(plans.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(items.findAllByPlanIdAndPlanDateOrderByPositionAsc(any(), any())).thenReturn(List.of());
        when(items.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(items.findAllByOwnerIdAndPlanDateBetweenOrderByPlanDateDescPositionAsc(any(), any(), any()))
                .thenReturn(List.of());
        when(archive.summary("smoke", 5)).thenReturn(new com.guangge.Interview.practice.dto.PracticeArchiveView(
                "", 5, new com.guangge.Interview.practice.dto.PracticeArchiveView.Totals(0, 0), false,
                List.of(), List.of(),
                List.of(new com.guangge.Interview.practice.dto.PracticeArchiveView.DimensionStat("logic", 40, 1))));
        when(mistakes.summary("smoke", null, 0, 1)).thenReturn(
                new MistakeBookView("", 75, 0, 0, 0, null, List.of(), List.of()));
    }

    @Test
    @DisplayName("今日计划：1 道薄弱项 + 2 道岗位高频，共 3 题")
    void generatesThreeItems() {
        DailyPlanView view = service.today("smoke", null);

        assertThat(view.totalCount()).isEqualTo(3);
        assertThat(view.items()).extracting(PlanItemView::kind)
                .containsExactly("weak_dimension", "frequent", "frequent");
        assertThat(view.items().get(0).title()).contains("回答逻辑");
        assertThat(view.goalTitle()).contains("Light").contains("Java 后端");
    }

    @Test
    @DisplayName("有错题时第二条换成错题重练，并记住题号")
    void usesMistakeWhenAvailable() {
        when(mistakes.summary("smoke", null, 0, 1)).thenReturn(
                new MistakeBookView("", 75, 1, 0, 0, null, List.of(),
                        List.of(new MistakeBookView.Mistake("q-9", "讲一个你优化过的接口", "technical",
                                "Java 后端", "standard", "technical", "balanced", 1, 40.0, 40.0,
                                Instant.now(), "logic", 30.0))));

        DailyPlanView view = service.today("smoke", null);

        assertThat(view.items().get(1).kind()).isEqualTo("mistake");
        assertThat(view.items().get(1).title()).contains("错题重练");
    }

    @Test
    @DisplayName("开始计划项：创建单题练习并回挂目标")
    void startCreatesSingleQuestionSession() {
        StudyPlanEntity plan = plan();
        StudyPlanItemEntity item = item(plan, "frequent", null);
        when(plans.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(items.findByIdAndOwnerId(item.getId(), "smoke")).thenReturn(Optional.of(item));
        PracticeSessionView view = mock(PracticeSessionView.class);
        UUID sessionId = UUID.randomUUID();
        when(view.id()).thenReturn(sessionId);
        when(sessions.create(eq("smoke"), any())).thenReturn(view);

        PracticeSessionView started = service.start("smoke", item.getId());

        assertThat(started.id()).isEqualTo(sessionId);
        assertThat(item.getSessionId()).isEqualTo(sessionId);
        assertThat(item.getStatus()).isEqualTo("in_progress");
        ArgumentCaptor<com.guangge.Interview.practice.dto.PracticeSessionRequest> captor =
                ArgumentCaptor.forClass(com.guangge.Interview.practice.dto.PracticeSessionRequest.class);
        verify(sessions).create(eq("smoke"), captor.capture());
        assertThat(captor.getValue().goalId()).isEqualTo(goal.getId());
        assertThat(captor.getValue().questions()).hasSize(1);
    }

    @Test
    @DisplayName("连续记录：今天完成 2 题算 1 天，连续两天算 2")
    void countsStreak() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        List<StudyPlanItemEntity> rows = new ArrayList<>();
        rows.addAll(List.of(completed(today), completed(today)));
        rows.addAll(List.of(completed(today.minusDays(1)), completed(today.minusDays(1))));
        rows.add(completed(today.minusDays(3)));
        when(items.findAllByOwnerIdAndPlanDateBetweenOrderByPlanDateDescPositionAsc(any(), any(), any()))
                .thenReturn(rows);

        DailyPlanView.StreakView streak = service.streak("smoke");

        assertThat(streak.current()).isEqualTo(2);
        assertThat(streak.longest()).isEqualTo(2);
    }

    private StudyPlanEntity plan() {
        StudyPlanEntity plan = new StudyPlanEntity();
        plan.setId(UUID.randomUUID());
        plan.setOwnerId("smoke");
        plan.setGoalId(goal.getId());
        plan.setStartDate(LocalDate.now(ZoneId.of("Asia/Shanghai")));
        plan.setEndDate(plan.getStartDate().plusDays(6));
        plan.setStatus("active");
        plan.setCreatedAt(Instant.now());
        return plan;
    }

    private StudyPlanItemEntity item(StudyPlanEntity plan, String kind, String ref) {
        StudyPlanItemEntity item = new StudyPlanItemEntity();
        item.setId(UUID.randomUUID());
        item.setPlanId(plan.getId());
        item.setOwnerId("smoke");
        item.setPlanDate(plan.getStartDate());
        item.setKind(kind);
        item.setQuestionRef(ref);
        item.setTitle("岗位高频：Java 后端");
        item.setEstimatedMinutes(5);
        item.setStatus("pending");
        item.setPosition(1);
        item.setCreatedAt(Instant.now());
        return item;
    }

    private static StudyPlanItemEntity completed(LocalDate date) {
        StudyPlanItemEntity item = new StudyPlanItemEntity();
        item.setId(UUID.randomUUID());
        item.setPlanDate(date);
        item.setStatus("completed");
        item.setOwnerId("smoke");
        return item;
    }
}
