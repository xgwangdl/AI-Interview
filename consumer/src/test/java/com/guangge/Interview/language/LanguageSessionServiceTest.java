package com.guangge.Interview.language;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.practice.storage.MediaStorage;
import com.guangge.Interview.quota.ModelCallGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话编排：开一场、说一轮、结束出报告。
 *
 * 主流程用"手打一轮"跑（不依赖麦克风与 ffmpeg），语音链路单独由
 * VoiceCapabilityProbe 与真实 E2E 脚本验证。
 */
class LanguageSessionServiceTest {

    private final LanguageSessionRepository sessions = mock(LanguageSessionRepository.class);
    private final LanguageTurnRepository turns = mock(LanguageTurnRepository.class);
    private final LanguageCatalog catalog = new LanguageCatalog();
    private final LanguageCoach coach = mock(LanguageCoach.class);
    private final LanguageSpeechService speech = mock(LanguageSpeechService.class);
    private final MediaStorage mediaStorage = mock(MediaStorage.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private LanguageSessionService service;
    private LanguageSessionEntity session;

    @BeforeEach
    void setUp() {
        service = new LanguageSessionService(sessions, turns, catalog, coach, speech, mediaStorage,
                ModelCallGuard.inMemory(true, 100, 3), objectMapper, 7);
        session = new LanguageSessionEntity();
        session.setId(UUID.randomUUID());
        session.setOwnerId("smoke");
        session.setLanguage("en");
        session.setScenario("interview");
        session.setLevel("intermediate");
        session.setStatus(LanguageSessionService.ACTIVE);
        session.setTurnCount(0);
        session.setMaxTurns(8);
        when(sessions.findByIdAndOwnerId(session.getId(), "smoke")).thenReturn(Optional.of(session));
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(turns.findAllBySessionIdOrderByTurnIndexAsc(session.getId())).thenReturn(List.of());
    }

    @Test
    @DisplayName("开一场：把旧的进行中会话标成 abandoned，而不是把用户挡在门外")
    void creatingAbandonsPreviousActiveSessions() {
        LanguageSessionEntity stale = new LanguageSessionEntity();
        stale.setId(UUID.randomUUID());
        stale.setStatus(LanguageSessionService.ACTIVE);
        when(sessions.findAllByOwnerIdAndStatus("smoke", LanguageSessionService.ACTIVE)).thenReturn(List.of(stale));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LanguageSessionView view = service.create("smoke",
                new LanguageSessionRequest("en", "daily", "beginner", null));

        assertThat(stale.getStatus()).isEqualTo(LanguageSessionService.ABANDONED);
        assertThat(view.language()).isEqualTo("en");
        assertThat(view.maxTurns()).isEqualTo(LanguageCatalog.DEFAULT_MAX_TURNS);
        assertThat(view.level()).isEqualTo("beginner");
        assertThat(view.opening()).contains("welcome in");
        assertThat(view.turns()).isEmpty();
        assertThat(view.serverVoice()).isTrue();
    }

    @Test
    @DisplayName("手打一轮：存一条轮次、推进轮数、返回回复与建议")
    void textTurnStoresTheExchange() throws Exception {
        when(coach.reply(any())).thenReturn(new LanguageCoachResult(
                "Nice — what was the hardest part?",
                List.of(new LanguageFeedback("I lead", "I led", "时态"))));
        when(turns.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LanguageTurnView turn = service.turnText("smoke", session.getId(), "I lead a migration.");

        assertThat(turn.turnIndex()).isEqualTo(1);
        assertThat(turn.transcript()).isEqualTo("I lead a migration.");
        assertThat(turn.replyText()).contains("hardest part");
        assertThat(turn.feedback()).hasSize(1);
        assertThat(turn.browserVoice()).isFalse();
        assertThat(session.getTurnCount()).isEqualTo(1);

        ArgumentCaptor<LanguageTurnEntity> captor = ArgumentCaptor.forClass(LanguageTurnEntity.class);
        verify(turns).save(captor.capture());
        assertThat(captor.getValue().getFeedbackJson()).contains("I led");
        assertThat(captor.getValue().getStatus()).isEqualTo("answered");
    }

    @Test
    @DisplayName("额度用完：这一轮直接 429，不调用模型")
    void respectsTheQuota() throws Exception {
        LanguageSessionService limited = new LanguageSessionService(sessions, turns, catalog, coach, speech,
                mediaStorage, ModelCallGuard.inMemory(true, 1, 3), objectMapper, 7);
        when(coach.reply(any())).thenReturn(new LanguageCoachResult("ok", List.of()));
        when(turns.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        limited.turnText("smoke", session.getId(), "first");
        assertThatThrownBy(() -> limited.turnText("smoke", session.getId(), "second"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("说满轮数就挡住")
    void stopsAtTheTurnLimit() {
        session.setTurnCount(8);

        assertThatThrownBy(() -> service.turnText("smoke", session.getId(), "one more"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("说满");
    }

    @Test
    @DisplayName("结束：生成报告并落库；再点一次直接返回，不再扣额度")
    void finishingIsIdempotent() {
        session.setTurnCount(1);
        LanguageTurnEntity turn = new LanguageTurnEntity();
        turn.setId(UUID.randomUUID());
        turn.setSessionId(session.getId());
        turn.setTurnIndex(1);
        turn.setTranscript("I led a migration.");
        turn.setReplyText("Tell me more.");
        when(turns.findAllBySessionIdOrderByTurnIndexAsc(session.getId())).thenReturn(List.of(turn));
        when(coach.report(any())).thenReturn(new LanguageReportResult(80, 70, 75, 85,
                "表达清楚。", List.of("a", "b", "c")));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LanguageSessionView first = service.finish("smoke", session.getId());
        LanguageSessionView second = service.finish("smoke", session.getId());

        assertThat(first.status()).isEqualTo(LanguageSessionService.FINISHED);
        assertThat(first.report().fluency()).isEqualTo(80);
        assertThat(second.report().improvements()).hasSize(3);
        verify(coach).report(any());
    }

    @Test
    @DisplayName("一轮都没说就结束 → 409，而不是给一份空报告")
    void refusesToFinishAnEmptySession() {
        assertThatThrownBy(() -> service.finish("smoke", session.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(coach, never()).report(any());
    }

    @Test
    @DisplayName("语言下线后，旧会话被跳过：列表照常打开，直接访问给 404（用户反馈的“语言配置丢失”）")
    void skipsSessionsWhoseLanguageIsGone() {
        LanguageSessionEntity legacy = new LanguageSessionEntity();
        legacy.setId(UUID.randomUUID());
        legacy.setOwnerId("smoke");
        legacy.setLanguage("ja");
        legacy.setScenario("daily");
        legacy.setStatus(LanguageSessionService.ABANDONED);
        when(sessions.findAllByOwnerIdOrderByCreatedAtDesc("smoke")).thenReturn(List.of(legacy));
        when(sessions.findByIdAndOwnerId(legacy.getId(), "smoke")).thenReturn(Optional.of(legacy));

        assertThat(service.list("smoke")).isEmpty();
        assertThatThrownBy(() -> service.get("smoke", legacy.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
