package com.guangge.Interview.jobradar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guangge.Interview.quota.ModelCallGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobEvaluationRunnerRecoveryTest {
    private JobLeadRepository leads;
    private JobEvaluationRunner runner;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        leads = mock(JobLeadRepository.class);
        runner = new JobEvaluationRunner(
                leads,
                mock(JobEvaluationRepository.class),
                mock(JobAgentRunService.class),
                mock(SkillProfileService.class),
                mock(MatchScorer.class),
                mock(JobRadarAiGateway.class),
                mock(RuleBasedSkillExtractor.class),
                new JobLeadStatusMachine(),
                mock(ModelCallGuard.class),
                objectMapper,
                "v1",
                15);
    }

    @Test
    void terminalFailureRestoresTheStatusFromTheRunInput() {
        UUID leadId = UUID.randomUUID();
        JobLeadEntity lead = lead(leadId, JobLeadStatusMachine.EVALUATING);
        JobAgentRunEntity run = run(leadId, "shortlisted");
        when(leads.findByIdAndOwnerId(leadId, "owner")).thenReturn(Optional.of(lead));

        runner.recoverTerminalFailure(run);

        assertThat(lead.getStatus()).isEqualTo(JobLeadStatusMachine.SHORTLISTED);
        verify(leads).save(lead);
    }

    @Test
    void terminalFailureDoesNotOverwriteAConcurrentUserDecision() {
        UUID leadId = UUID.randomUUID();
        JobLeadEntity lead = lead(leadId, JobLeadStatusMachine.ARCHIVED);
        when(leads.findByIdAndOwnerId(leadId, "owner")).thenReturn(Optional.of(lead));

        runner.recoverTerminalFailure(run(leadId, "shortlisted"));

        assertThat(lead.getStatus()).isEqualTo(JobLeadStatusMachine.ARCHIVED);
    }

    private JobAgentRunEntity run(UUID leadId, String previousStatus) {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("previousStatus", previousStatus);
        JobAgentRunEntity run = new JobAgentRunEntity();
        run.setId(UUID.randomUUID());
        run.setOwnerId("owner");
        run.setKind(JobAgentRunService.KIND_JOB_EVALUATE);
        run.setSubjectId(leadId);
        run.setInputJson(input);
        return run;
    }

    private static JobLeadEntity lead(UUID id, String status) {
        JobLeadEntity lead = new JobLeadEntity();
        lead.setId(id);
        lead.setOwnerId("owner");
        lead.setStatus(status);
        return lead;
    }
}
