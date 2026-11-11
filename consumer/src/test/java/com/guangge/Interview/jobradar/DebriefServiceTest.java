package com.guangge.Interview.jobradar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DebriefServiceTest {
    private InterviewDebriefRepository debriefs;
    private JobLeadService leads;
    private PracticeSessionRepository practiceSessions;
    private JobAgentRunService runs;
    private WeakPointService weakPoints;
    private DebriefService service;

    @BeforeEach
    void setUp() {
        debriefs = mock(InterviewDebriefRepository.class);
        leads = mock(JobLeadService.class);
        practiceSessions = mock(PracticeSessionRepository.class);
        runs = mock(JobAgentRunService.class);
        weakPoints = mock(WeakPointService.class);
        service = new DebriefService(debriefs, leads, practiceSessions, runs, weakPoints, new ObjectMapper());
    }

    @Test
    void explicitPrivacyAcknowledgementIsRequiredBeforeAnyWrite() {
        DebriefCreateRequest request = request("这是一次真实面试的详细记录，包含 Redis 和项目追问。", false);

        assertThatThrownBy(() -> service.create("alice", request))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getReason()).contains("隐私提示");
                });

        verifyNoInteractions(debriefs, leads, practiceSessions, runs, weakPoints);
    }

    @Test
    void rawDebriefTextStaysOutOfAgentInputAndLogs() {
        String rawText = "一面被追问：Redis 缓存雪崩怎么处理？项目中最难的技术决策是什么？";
        JobAgentRunEntity run = new JobAgentRunEntity();
        run.setId(UUID.randomUUID());
        when(runs.enqueue(eq("alice"), eq(JobAgentRunService.KIND_DEBRIEF_PARSE),
                eq("interview_debrief"), any(UUID.class), anyString(), any()))
                .thenReturn(new JobAgentRunService.EnqueueResult(run, false));

        DebriefCreateResponse response = service.create("alice", request(rawText, true));

        ArgumentCaptor<InterviewDebriefEntity> debriefCaptor = ArgumentCaptor.forClass(InterviewDebriefEntity.class);
        verify(debriefs).save(debriefCaptor.capture());
        assertThat(debriefCaptor.getValue().getRawText()).isEqualTo(rawText);
        assertThat(debriefCaptor.getValue().getPrivacyAcknowledgedAt()).isNotNull();

        ArgumentCaptor<JsonNode> inputCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(runs).enqueue(eq("alice"), eq(JobAgentRunService.KIND_DEBRIEF_PARSE),
                eq("interview_debrief"), any(UUID.class), anyString(), inputCaptor.capture());
        assertThat(inputCaptor.getValue().path("rawTextLength").asInt()).isEqualTo(rawText.length());
        assertThat(inputCaptor.getValue().toString()).doesNotContain(rawText).doesNotContain("Redis 缓存雪崩");
        assertThat(response.runId()).isEqualTo(run.getId());
        assertThat(response.parseStatus()).isEqualTo("pending");
        verify(weakPoints, never()).replaceForDebrief(anyString(), any(UUID.class), any(), any());
    }

    private static DebriefCreateRequest request(String rawText, boolean acknowledged) {
        return new DebriefCreateRequest(null, null, LocalDate.now(), "一面", "unknown", rawText,
                "Redis,项目", "需要继续练习缓存与项目复盘", acknowledged);
    }
}
