package com.guangge.Interview.cohort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CohortServiceTest {

    private final CohortRepository cohorts = mock(CohortRepository.class);
    private final CohortMemberRepository members = mock(CohortMemberRepository.class);
    private final CohortService service = new CohortService(cohorts, members);

    @BeforeEach
    void defaults() {
        when(cohorts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(members.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CohortMemberEntity member = new CohortMemberEntity();
        member.setCohortId(UUID.randomUUID());
        member.setOwnerId("alice");
        member.setRole("coach");
        member.setJoinedAt(Instant.now());
        when(members.findAllByCohortIdOrderByJoinedAtAsc(any())).thenReturn(List.of(member));
    }

    @Test
    @DisplayName("创建小组：创建者是 coach，并生成邀请码")
    void createsCoachCohort() {
        CohortView view = service.create("alice", new CohortRequest("2026 秋招 Java"));

        assertThat(view.role()).isEqualTo("coach");
        assertThat(view.inviteCode()).hasSize(8);
        assertThat(view.memberCount()).isEqualTo(1);
        verify(members).save(any());
    }

    @Test
    @DisplayName("邀请码加入：已有成员不重复加入，新成员是 learner")
    void joinsByInviteCode() {
        CohortEntity cohort = new CohortEntity();
        cohort.setId(UUID.randomUUID());
        cohort.setName("小组");
        cohort.setInviteCode("ABC12345");
        cohort.setOwnerId("coach");
        cohort.setCreatedAt(Instant.now());
        when(cohorts.findByInviteCodeIgnoreCase("ABC12345")).thenReturn(Optional.of(cohort));
        when(members.findByCohortIdAndOwnerId(cohort.getId(), "bob")).thenReturn(Optional.empty());

        CohortView joined = service.join("bob", "ABC12345");

        assertThat(joined.role()).isEqualTo("learner");
        verify(members).save(any());
    }

    @Test
    @DisplayName("学员可以自己开启/关闭分数共享")
    void togglesScoreSharing() {
        UUID id = UUID.randomUUID();
        CohortEntity cohort = new CohortEntity();
        cohort.setId(id);
        cohort.setName("小组");
        cohort.setInviteCode("ABC12345");
        cohort.setOwnerId("coach");
        cohort.setCreatedAt(Instant.now());
        CohortMemberEntity member = new CohortMemberEntity();
        member.setCohortId(id);
        member.setOwnerId("bob");
        member.setRole("learner");
        member.setJoinedAt(Instant.now());
        when(cohorts.findById(id)).thenReturn(Optional.of(cohort));
        when(members.findByCohortIdAndOwnerId(id, "bob")).thenReturn(Optional.of(member));

        service.setScoreSharing("bob", id, true);

        assertThat(member.isShareScores()).isTrue();
        verify(members).save(member);
    }
}
