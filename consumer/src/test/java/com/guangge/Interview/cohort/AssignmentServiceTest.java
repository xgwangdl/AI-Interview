package com.guangge.Interview.cohort;

import com.guangge.Interview.goal.StudyPlanItemRepository;
import com.guangge.Interview.goal.StudyPlanRepository;
import com.guangge.Interview.practice.repository.PracticeScoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
}
