package com.guangge.Interview.practice.followup;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.guangge.Interview.quota.ModelCallGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 追问的判定与降级。
 *
 * 这一块最怕的是"把练习卡住"：所以不追问、转写太短、达到上限、模型判断为 null、
 * 额度用完、甚至转写/模型报错，都必须让用户能继续答下一题。
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

        service = service(ModelCallGuard.inMemory(true, 100, 3));
    }

    private PracticeFollowUpService service(ModelCallGuard guard) {
        return new PracticeFollowUpService(sessions, answers, mediaStorage,
                (media, contentType) -> new TranscriptResult(transcript, List.of()),
                provider, new PracticeQuestionCatalog(new ObjectMapper()), sessionService, guard, 5000);
    }

    @Test
    @DisplayName("回答有料 → 现场转写 + 生成追问，并插在被打断的那道题后面")
    void insertsFollowUpQuestionAfterItsParent() {
        PracticeQuestion created = new PracticeQuestion("q-0-follow-1", "你说的优化具体改了什么？", "follow_up");
        when(provider.followUp(any()))
                .thenReturn(new PracticeFollowUpResult("你说的优化具体改了什么？", "只给了结论没讲做法"));
        when(sessionService.appendFollowUpQuestion(eq(sessionId), eq(0), eq("q-1"), anyString(), eq("follow_up")))
                .thenReturn(created);

        PracticeFollowUpView view = service.followUp("smoke", sessionId, answerId);

        assertThat(view.question()).isEqualTo(created);
        assertThat(view.reason()).isEqualTo("只给了结论没讲做法");
        assertThat(view.model()).isEqualTo("qwen-max");
        assertThat(view.round()).isEqualTo(1);
        // 位置必须跟着原题：插到末尾会让后面每一题的 questionIndex 错位，保存回答直接 400
        verify(sessionService).appendFollowUpQuestion(sessionId, 0, "q-1", "你说的优化具体改了什么？", "follow_up");
        // 当场转写的结果要落回回答上，后面的批量分析就不用再转一次
        verify(sessionService).saveTranscript(answerId, transcript);
    }

    @Test
    @DisplayName("连续追问：第二轮带着前一轮的问答去问，且说明这是第几轮")
    void secondRoundCarriesTheWholeChain() {
        session.setQuestionsJson("""
                [{"id":"q-1","text":"讲一个你优化过的接口","category":"technical"},
                 {"id":"q-0-follow-1","text":"你说的优化具体改了什么？","category":"follow_up"}]
                """);
        answer.setQuestionIndex(1);
        answer.setQuestionId("q-0-follow-1");
        when(answers.findAllBySessionIdOrderByQuestionIndexAsc(sessionId)).thenReturn(List.of(answer));
        when(provider.followUp(any()))
                .thenReturn(new PracticeFollowUpResult("合并之后有没有引入新的问题？", "还是没讲取舍"));
        when(sessionService.appendFollowUpQuestion(any(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn(new PracticeQuestion("q-1-follow-2", "合并之后有没有引入新的问题？", "follow_up"));

        PracticeFollowUpView view = service.followUp("smoke", sessionId, answerId);

        assertThat(view.round()).isEqualTo(2);
        ArgumentCaptor<PracticeFollowUpContext> captor = ArgumentCaptor.forClass(PracticeFollowUpContext.class);
        verify(provider).followUp(captor.capture());
        PracticeFollowUpContext context = captor.getValue();
        assertThat(context.rootQuestion()).isEqualTo("讲一个你优化过的接口");
        assertThat(context.turns()).hasSize(2);
        assertThat(context.turns().get(1).question()).isEqualTo("你说的优化具体改了什么？");
        assertThat(context.turns().get(1).answer()).isEqualTo(transcript);
        assertThat(context.renderTurns()).contains("[原题]").contains("[第 1 次追问]");
    }

    @Test
    @DisplayName("同一道题最多追问到第 2 轮：第三轮停下来，别变成审问")
    void stopsAfterTwoRoundsOnTheSameQuestion() {
        // 追问插在原题后面，所以链是 q-1 → 追问一 → 追问二，后面才是下一道正题
        session.setQuestionsJson("""
                [{"id":"q-1","text":"讲一个你优化过的接口","category":"technical"},
                 {"id":"q-0-follow-1","text":"追问一","category":"follow_up"},
                 {"id":"q-1-follow-2","text":"追问二","category":"follow_up"},
                 {"id":"q-2","text":"讲讲你做过的系统设计","category":"system"}]
                """);
        answer.setQuestionIndex(2);
        answer.setQuestionId("q-1-follow-2");

        PracticeFollowUpView view = service.followUp("smoke", sessionId, answerId);

        assertThat(view.question()).isNull();
        assertThat(view.reason()).contains("已经追问过");
        // 一场的上限（3 次）还没到，挡下来的就是"同一道题只追问两轮"
        assertThat(view.reason()).doesNotContain("这场练习");
        verify(provider, never()).followUp(any());
    }

    @Test
    @DisplayName("额度用完只是少问一句：不报错，练习照常继续")
    void skipsWhenTheDailyQuotaIsGone() {
        PracticeFollowUpService limited = service(ModelCallGuard.inMemory(true, 1, 3));
        when(provider.followUp(any())).thenReturn(new PracticeFollowUpResult(null, "回答已经比较完整"));

        assertThat(limited.followUp("smoke", sessionId, answerId).question()).isNull();   // 用掉唯一一次
        PracticeFollowUpView view = limited.followUp("smoke", sessionId, answerId);

        assertThat(view.question()).isNull();
        assertThat(view.reason()).contains("额度");
        // 第二次连模型都没碰：这才是"额度护栏"该有的样子
        verify(provider, times(1)).followUp(any());
    }

    @Test
    @DisplayName("已经有转写就不再调 ASR（省一次调用）")
    void reusesExistingTranscript() {
        answer.setTranscriptText(transcript);
        when(provider.followUp(any()))
                .thenReturn(new PracticeFollowUpResult(null, "回答已经比较完整"));

        PracticeFollowUpView view = service.followUp("smoke", sessionId, answerId);

        assertThat(view.question()).isNull();
        assertThat(view.reason()).isEqualTo("回答已经比较完整");
        verify(sessionService, never()).saveTranscript(any(), anyString());
        verify(sessionService, never()).appendFollowUpQuestion(any(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("回答太短就不追问：没听清或没答，追问也问不出东西")
    void skipsTooShortAnswers() {
        transcript = "嗯，还行。";

        PracticeFollowUpView view = service.followUp("smoke", sessionId, answerId);

        assertThat(view.question()).isNull();
        assertThat(view.reason()).contains("太短");
        verify(provider, never()).followUp(any());
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
        verify(provider, never()).followUp(any());
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
