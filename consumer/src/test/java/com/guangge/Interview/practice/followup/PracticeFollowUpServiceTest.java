package com.guangge.Interview.practice.followup;

import com.guangge.Interview.analysis.TranscriptResult;
import com.guangge.Interview.practice.dto.PracticeFollowUpView;
import com.guangge.Interview.practice.dto.PracticeQuestion;
import com.guangge.Interview.practice.entity.PracticeAnswerEntity;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.repository.PracticeAnswerRepository;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import com.guangge.Interview.practice.service.PracticeQuestionCatalog;
import com.guangge.Interview.practice.service.PracticeSessionService;
import com.guangge.Interview.practice.storage.MediaStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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

/**
 * 追问的判定与降级。
 *
 * 这一块最怕的是"把练习卡住"：所以不追问、转写太短、达到上限、模型判断为 null、
 * 甚至转写/模型报错，都必须让用户能继续答下一题。
 */
class PracticeFollowUpServiceTest {

    private final PracticeSessionRepository sessions = mock(PracticeSessionRepository.class);
    private final PracticeAnswerRepository answers = mock(PracticeAnswerRepository.class);
    private final MediaStorage mediaStorage = mock(MediaStorage.class);
    private final PracticeFollowUpProvider provider = mock(PracticeFollowUpProvider.class);
    private final PracticeSessionService sessionService = mock(PracticeSessionService.class);

    private final UUID sessionId = UUID.randomUUID();
    private final UUID answerId = UUID.randomUUID();

    private PracticeSessionEntity session;
    private PracticeAnswerEntity answer;
    private PracticeFollowUpService service;

    /** 转写结果由测试按需替换。 */
    private String transcript = "我在上一家公司负责订单系统，把三次库调用合并成一次，耗时从 800ms 降到 120ms。";

    @BeforeEach
    void setUp() {
        session = new PracticeSessionEntity();
        session.setId(sessionId);
        session.setOwnerId("smoke");
        session.setRole("Java 后端工程师");
        session.setStatus("in_progress");
        session.setFollowUpEnabled(true);
        session.setQuestionsJson("""
                [{"id":"q-1","text":"讲一个你优化过的接口","category":"technical"}]
                """);
        answer = new PracticeAnswerEntity();
        answer.setId(answerId);
        answer.setSessionId(sessionId);
        answer.setQuestionIndex(0);
        answer.setQuestionId("q-1");
        answer.setMediaKey("media-key");
        answer.setMediaContentType("video/webm");

        when(sessions.findByIdAndOwnerId(sessionId, "smoke")).thenReturn(Optional.of(session));
        when(answers.findById(answerId)).thenReturn(Optional.of(answer));
        when(mediaStorage.find("media-key"))
                .thenReturn(Optional.of(new ByteArrayResource("clip".getBytes())));
        when(provider.modelName()).thenReturn("qwen-max");

        service = new PracticeFollowUpService(sessions, answers, mediaStorage,
                (media, contentType) -> new TranscriptResult(transcript, java.util.List.of()),
                provider, new PracticeQuestionCatalog(new ObjectMapper()), sessionService, 5000);
    }

    @Test
    @DisplayName("回答有料 → 现场转写 + 生成追问，并把它追加成下一题")
    void appendsFollowUpQuestion() {
        PracticeQuestion created = new PracticeQuestion("q-1-follow-1", "你说的优化具体改了什么？", "follow_up");
        when(provider.followUp(eq("Java 后端工程师"), any(), eq("讲一个你优化过的接口"), anyString()))
                .thenReturn(new PracticeFollowUpResult("你说的优化具体改了什么？", "只给了结论没讲做法"));
        when(sessionService.appendFollowUpQuestion(sessionId, "你说的优化具体改了什么？", "follow_up"))
                .thenReturn(created);

        PracticeFollowUpView view = service.followUp("smoke", sessionId, answerId);

        assertThat(view.question()).isEqualTo(created);
        assertThat(view.reason()).isEqualTo("只给了结论没讲做法");
        assertThat(view.model()).isEqualTo("qwen-max");
        // 当场转写的结果要落回回答上，后面的批量分析就不用再转一次
        verify(sessionService).saveTranscript(answerId, transcript);
    }

    @Test
    @DisplayName("已经有转写就不再调 ASR（省一次调用）")
    void reusesExistingTranscript() {
        answer.setTranscriptText(transcript);
        when(provider.followUp(any(), any(), any(), anyString()))
                .thenReturn(new PracticeFollowUpResult(null, "回答已经比较完整"));

        PracticeFollowUpView view = service.followUp("smoke", sessionId, answerId);

        assertThat(view.question()).isNull();
        assertThat(view.reason()).isEqualTo("回答已经比较完整");
        verify(sessionService, never()).saveTranscript(any(), anyString());
        verify(sessionService, never()).appendFollowUpQuestion(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("回答太短就不追问：没听清或没答，追问也问不出东西")
    void skipsTooShortAnswers() {
        transcript = "嗯，还行。";

        PracticeFollowUpView view = service.followUp("smoke", sessionId, answerId);

        assertThat(view.question()).isNull();
        assertThat(view.reason()).contains("太短");
        verify(provider, never()).followUp(any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("一场最多追问 3 次：够了就说清楚，而不是继续追问")
    void stopsAfterThreeFollowUps() {
        session.setQuestionsJson("""
                [{"id":"q-1","text":"讲一个你优化过的接口","category":"technical"},
                 {"id":"q-1-follow-1","text":"追问一","category":"follow_up"},
                 {"id":"q-1-follow-2","text":"追问二","category":"follow_up"},
                 {"id":"q-1-follow-3","text":"追问三","category":"follow_up"}]
                """);

        PracticeFollowUpView view = service.followUp("smoke", sessionId, answerId);

        assertThat(view.question()).isNull();
        assertThat(view.reason()).contains("追问已经够多");
        verify(provider, never()).followUp(any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("没开追问的练习、别人的回答、别人的场次都要被挡住")
    void guardsAccessAndFlag() {
        session.setFollowUpEnabled(false);
        assertThatThrownBy(() -> service.followUp("smoke", sessionId, answerId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        session.setFollowUpEnabled(true);
        assertThatThrownBy(() -> service.followUp("intruder", sessionId, answerId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        PracticeAnswerEntity foreign = new PracticeAnswerEntity();
        foreign.setId(answerId);
        foreign.setSessionId(UUID.randomUUID());
        when(answers.findById(answerId)).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.followUp("smoke", sessionId, answerId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
