package com.guangge.Interview.practice.rewrite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.practice.entity.AnswerRewriteEntity;
import com.guangge.Interview.practice.entity.PracticeAnswerEntity;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.repository.AnswerRewriteRepository;
import com.guangge.Interview.practice.repository.PracticeAnswerRepository;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import com.guangge.Interview.practice.service.PracticeQuestionCatalog;
import com.guangge.Interview.quota.ModelCallGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnswerRewriteServiceTest {

    private final PracticeAnswerRepository answers = mock(PracticeAnswerRepository.class);
    private final PracticeSessionRepository sessions = mock(PracticeSessionRepository.class);
    private final AnswerRewriteRepository rewrites = mock(AnswerRewriteRepository.class);
    private final AnswerRewriteProvider provider = mock(AnswerRewriteProvider.class);
    private final AnswerRewriteService service = new AnswerRewriteService(
            answers, sessions, rewrites, new PracticeQuestionCatalog(new ObjectMapper()), provider,
            new AnswerRewriteValidator(), ModelCallGuard.inMemory(true, 100, 3));

    private PracticeAnswerEntity answer;
    private PracticeSessionEntity session;

    @BeforeEach
    void setUp() {
        session = new PracticeSessionEntity();
        session.setId(UUID.randomUUID());
        session.setOwnerId("smoke");
        session.setRole("Java 后端");
        session.setJobDescription("订单系统");
        session.setQuestionsJson("[{\"id\":\"q-1\",\"text\":\"讲一个项目\",\"category\":\"项目\"}]");
        answer = new PracticeAnswerEntity();
        answer.setId(UUID.randomUUID());
        answer.setSessionId(session.getId());
        answer.setQuestionId("q-1");
        answer.setTranscriptText("我优化了接口，从 900ms 降到 100ms。");
        answer.setCreatedAt(Instant.now());
        when(answers.findById(answer.getId())).thenReturn(Optional.of(answer));
        when(sessions.findByIdAndOwnerId(session.getId(), "smoke")).thenReturn(Optional.of(session));
        when(answers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("AI 改写落库并写历史；只改表达不改数字")
    void generatesAndStoresRewrite() {
        when(provider.rewrite("Java 后端", "订单系统", "讲一个项目", answer.getTranscriptText()))
                .thenReturn("我把接口从 900ms 优化到 100ms，先定位慢查询，再减少重复调用。");

        var view = service.generate("smoke", answer.getId());

        assertThat(view.rewriteText()).contains("900ms").contains("100ms");
        assertThat(view.source()).isEqualTo("ai");
        assertThat(answer.getRewriteText()).isEqualTo(view.rewriteText());
        ArgumentCaptor<AnswerRewriteEntity> captor = ArgumentCaptor.forClass(AnswerRewriteEntity.class);
        verify(rewrites).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("ai");
    }

    @Test
    @DisplayName("用户编辑覆盖当前改写，并记录为 user")
    void userEditIsStoredAsUserSource() {
        var view = service.edit("smoke", answer.getId(), "我在 2024 年把接口从 900ms 优化到 100ms。");

        assertThat(view.source()).isEqualTo("user");
        assertThat(answer.getRewriteText()).contains("2024");
    }
}
