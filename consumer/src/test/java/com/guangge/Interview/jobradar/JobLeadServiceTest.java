package com.guangge.Interview.jobradar;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobLeadServiceTest {
    private JobLeadRepository leads;
    private JobEvaluationRepository evaluations;
    private JobAgentRunRepository runs;
    private JobAgentRunService runService;
    private SkillProfileService skillProfiles;
    private JobLeadService service;

    @BeforeEach
    void setUp() {
        leads = mock(JobLeadRepository.class);
        evaluations = mock(JobEvaluationRepository.class);
        runs = mock(JobAgentRunRepository.class);
        runService = mock(JobAgentRunService.class);
        skillProfiles = mock(SkillProfileService.class);
        service = new JobLeadService(leads, evaluations, runs, runService, new JobLeadStatusMachine(),
                skillProfiles, new ObjectMapper(), 5, "Asia/Shanghai", "v1");
    }

    @Test
    void duplicateLeadIsReturnedForTheSameOwnerWithoutConsumingQuota() {
        JobLeadEntity existing = lead("alice", JobLeadStatusMachine.SHORTLISTED);
        when(leads.findByOwnerIdAndDedupKey(eq("alice"), anyString())).thenReturn(Optional.of(existing));

        JobLeadCreateResponse response = service.create("alice", request("Acme", "Java Engineer", validJd()));

        assertThat(response.duplicated()).isTrue();
        assertThat(response.leadId()).isEqualTo(existing.getId());
        verify(leads, never()).save(any(JobLeadEntity.class));
        verify(runs, never()).countByOwnerIdAndKindAndCreatedAtGreaterThanEqual(anyString(), anyString(), any());
    }

    @Test
    void sameDedupKeyCanBelongToDifferentOwners() {
        when(leads.findByOwnerIdAndDedupKey(eq("bob"), anyString())).thenReturn(Optional.empty());
        when(runs.countByOwnerIdAndKindAndCreatedAtGreaterThanEqual(eq("bob"), anyString(), any())).thenReturn(0L);
        when(skillProfiles.profileHash("bob")).thenReturn("profile-hash");
        JobAgentRunEntity queued = new JobAgentRunEntity();
        queued.setId(UUID.randomUUID());
        when(runService.enqueue(eq("bob"), anyString(), anyString(), any(UUID.class), anyString(), any()))
                .thenReturn(new JobAgentRunService.EnqueueResult(queued, false));

        JobLeadCreateResponse response = service.create("bob", request("Acme", "Java Engineer", validJd()));

        assertThat(response.duplicated()).isFalse();
        assertThat(response.runId()).isEqualTo(queued.getId());
        verify(leads).findByOwnerIdAndDedupKey(eq("bob"), anyString());
    }

    @Test
    void invalidSalaryRangeIsRejectedBeforeCreatingARun() {
        assertThatThrownBy(() -> service.create("alice",
                new JobLeadCreateRequest("Acme", "Java Engineer", "Shanghai", 50, 30, validJd(), null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(leads, never()).save(any(JobLeadEntity.class));
        verify(runService, never()).enqueue(anyString(), anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    void shortJobDescriptionIsRejected() {
        assertThatThrownBy(() -> service.create("alice", request("Acme", "Java Engineer", "too short")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void anotherOwnerCannotReadTheLead() {
        UUID leadId = UUID.randomUUID();
        when(leads.findByIdAndOwnerId(leadId, "bob")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("bob", leadId))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(error.getReason()).isEqualTo("岗位不存在");
                });
    }

    @Test
    void editingCompanyOrRoleRecomputesDedupAndRejectsAConflict() {
        JobLeadEntity current = lead("alice", JobLeadStatusMachine.SHORTLISTED);
        current.setCompany("Old Co");
        current.setRole("Java Engineer");
        current.setJdHash(JobRadarHashing.sha256(validJd()));
        JobLeadEntity duplicate = lead("alice", JobLeadStatusMachine.NEW);
        when(leads.findByIdAndOwnerId(current.getId(), "alice")).thenReturn(Optional.of(current));
        when(leads.findByOwnerIdAndDedupKey(eq("alice"), anyString())).thenReturn(Optional.of(duplicate));

        assertThatThrownBy(() -> service.patch("alice", current.getId(),
                new JobLeadPatchRequest("New Co", null, null, null, null, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.getReason()).contains("公司、岗位和 JD");
                });

        verify(leads, never()).save(current);
    }

    private static JobLeadCreateRequest request(String company, String role, String jdText) {
        return new JobLeadCreateRequest(company, role, "Shanghai", 25, 40, jdText, null);
    }

    private static JobLeadEntity lead(String owner, String status) {
        JobLeadEntity lead = new JobLeadEntity();
        lead.setId(UUID.randomUUID());
        lead.setOwnerId(owner);
        lead.setCompany("Acme");
        lead.setRole("Java Engineer");
        lead.setJdHash(JobRadarHashing.sha256(validJd()));
        lead.setDedupKey(JobRadarHashing.sha256("acme\njava engineer\n" + lead.getJdHash()));
        lead.setStatus(status);
        lead.setCreatedAt(Instant.now());
        lead.setUpdatedAt(Instant.now());
        return lead;
    }

    private static String validJd() {
        return "岗位职责：负责 Java 与 Spring Boot 后端服务的设计、开发、测试和线上稳定性建设。"
                + "任职要求：熟悉 Java、Spring Boot、PostgreSQL、Redis，具备分布式系统排障经验，"
                + "能够与产品、前端和测试团队协作交付。工作地点上海，薪资 25-40K。";
    }
}
