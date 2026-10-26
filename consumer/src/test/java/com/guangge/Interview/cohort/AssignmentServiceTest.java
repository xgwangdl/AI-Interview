package com.guangge.Interview.cohort;

import com.guangge.Interview.goal.StudyPlanItemRepository;
import com.guangge.Interview.goal.StudyPlanRepository;
import com.guangge.Interview.practice.repository.PracticeScoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssignmentServiceTest {

    @Test
    @DisplayName("布置作业会为每位学员生成计划项")
    void materializesForEveryLearner() {
        CohortService cohorts = mock(CohortService.class);
        CohortMemberRepository members = mock(CohortMemberRepository.class);
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        StudyPlanRepository plans = mock(StudyPlanRepository.class);
        StudyPlanItemRepository planItems = mock(StudyPlanItemRepository.class);
        PracticeScoreRepository scores = mock(PracticeScoreRepository.class);
        AssignmentService service = new AssignmentService(cohorts, members, assignments, plans, planItems, scores);

        UUID cohortId = UUID.randomUUID();
        CohortMemberEntity learner = new CohortMemberEntity();
        learner.setCohortId(cohortId);
        learner.setOwnerId("bob");
        learner.setRole("learner");
        learner.setJoinedAt(Instant.now());
        when(members.findAllByCohortIdOrderByJoinedAtAsc(cohortId)).thenReturn(List.of(learner));
        when(planItems.existsByAssignmentIdAndOwnerId(any(), eq("bob"))).thenReturn(false);
        when(assignments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(plans.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(planItems.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(planItems.countByAssignmentIdAndOwnerId(any(), eq("bob"))).thenReturn(15L);
        when(planItems.countByAssignmentIdAndOwnerIdAndStatus(any(), eq("bob"), eq("completed"))).thenReturn(0L);

        AssignmentView view = service.create("coach", cohortId,
                new AssignmentRequest("5 天训练", "说明", 5, 3, null));

        assertThat(view.memberCount()).isEqualTo(1);
        assertThat(view.myTotal()).isZero(); // coach 不生成自己的作业
        verify(planItems).saveAll(any());
    }

    @Test
    @DisplayName("无人开始作答时可以编辑天数与每天题目数，并重建计划项")
    void updatesStructureBeforeLearnersStart() {
        CohortService cohorts = mock(CohortService.class);
        CohortMemberRepository members = mock(CohortMemberRepository.class);
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        StudyPlanRepository plans = mock(StudyPlanRepository.class);
        StudyPlanItemRepository planItems = mock(StudyPlanItemRepository.class);
        PracticeScoreRepository scores = mock(PracticeScoreRepository.class);
        AssignmentService service = new AssignmentService(cohorts, members, assignments, plans, planItems, scores);

        UUID cohortId = UUID.randomUUID();
        AssignmentEntity assignment = new AssignmentEntity();
        assignment.setId(UUID.randomUUID());
        assignment.setCohortId(cohortId);
        assignment.setTitle("旧作业");
        assignment.setDays(5);
        assignment.setItemsPerDay(3);
        assignment.setCreatedAt(Instant.now());
        CohortMemberEntity learner = new CohortMemberEntity();
        learner.setCohortId(cohortId);
        learner.setOwnerId("bob");
        learner.setRole("learner");

        when(assignments.findById(assignment.getId())).thenReturn(java.util.Optional.of(assignment));
        when(assignments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(planItems.findAllByAssignmentId(assignment.getId())).thenReturn(List.of());
        when(members.findAllByCohortIdOrderByJoinedAtAsc(cohortId)).thenReturn(List.of(learner));
        when(planItems.existsByAssignmentIdAndOwnerId(assignment.getId(), "bob")).thenReturn(false);
        when(plans.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(planItems.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AssignmentView view = service.update("coach", cohortId, assignment.getId(),
                new AssignmentRequest("新作业", "新说明", 2, 2, null));

        assertThat(view.title()).isEqualTo("新作业");
        assertThat(view.days()).isEqualTo(2);
        assertThat(view.itemsPerDay()).isEqualTo(2);
        verify(planItems).saveAll(any());
    }

    @Test
    @DisplayName("已有作答进度时不能改作业结构，避免清空学员进度")
    void rejectsStructuralUpdateAfterProgress() {
        CohortService cohorts = mock(CohortService.class);
        CohortMemberRepository members = mock(CohortMemberRepository.class);
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        StudyPlanRepository plans = mock(StudyPlanRepository.class);
        StudyPlanItemRepository planItems = mock(StudyPlanItemRepository.class);
        PracticeScoreRepository scores = mock(PracticeScoreRepository.class);
        AssignmentService service = new AssignmentService(cohorts, members, assignments, plans, planItems, scores);

        UUID cohortId = UUID.randomUUID();
        AssignmentEntity assignment = new AssignmentEntity();
        assignment.setId(UUID.randomUUID());
        assignment.setCohortId(cohortId);
        assignment.setDays(5);
        assignment.setItemsPerDay(3);
        com.guangge.Interview.goal.StudyPlanItemEntity completed =
                new com.guangge.Interview.goal.StudyPlanItemEntity();
        completed.setStatus("completed");

        when(assignments.findById(assignment.getId())).thenReturn(java.util.Optional.of(assignment));
        when(planItems.findAllByAssignmentId(assignment.getId())).thenReturn(List.of(completed));

        assertThatThrownBy(() -> service.update("coach", cohortId, assignment.getId(),
                new AssignmentRequest("修改", null, 2, 2, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }
}
