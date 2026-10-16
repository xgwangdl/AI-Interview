package com.guangge.Interview.practice.mistakes;

import com.guangge.Interview.practice.dto.PracticeSessionRequest;
import com.guangge.Interview.practice.dto.PracticeSessionView;
import com.guangge.Interview.practice.entity.PracticeScoreEntity;
import com.guangge.Interview.practice.repository.PracticeScoreRepository;
import com.guangge.Interview.practice.service.PracticeSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 错题本：什么样的题算错题、同一道题重练几次算同一道、重练建出来的场次长什么样。
 */
class MistakeBookServiceTest {

    private final PracticeScoreRepository scores = mock(PracticeScoreRepository.class);
    private final PracticeSessionService sessions = mock(PracticeSessionService.class);
    private final MistakeBookService service = new MistakeBookService(scores, sessions);

    @Test
    @DisplayName("同一道题重练两次算一道：取最好的一次判断有没有过线")
    void groupsRepeatedAttemptsByQuestion() {
        when(scores.findScoredQuestions("smoke")).thenReturn(List.of(
                row("q-1", "讲一个你优化过的接口", 52.0, Instant.parse("2026-10-15T10:00:00Z")),
                row("q-1", "讲一个你优化过的接口", 48.0, Instant.parse("2026-10-14T10:00:00Z"))));

        MistakeBookView book = service.summary("smoke", 5);

        assertThat(book.mistakes()).hasSize(1);
        MistakeBookView.Mistake mistake = book.mistakes().get(0);
        assertThat(mistake.attempts()).isEqualTo(2);
        assertThat(mistake.bestScore()).isEqualTo(52.0);      // 不是平均，也不是最低
        assertThat(mistake.latestScore()).isEqualTo(52.0);    // 最近一次
        assertThat(mistake.text()).isEqualTo("讲一个你优化过的接口");
        assertThat(mistake.difficulty()).isEqualTo("standard");
        // 五个维度都给 60 分，只有 STAR 结构是 40 —— 这就是"这道题栽在哪"
        assertThat(mistake.weakestDimension()).isEqualTo("starStructure");
        assertThat(mistake.weakestScore()).isEqualTo(40.0);
    }

    @Test
    @DisplayName("最好一次过线就不算错题：重练过线后它自己会从本子里消失")
    void dropsQuestionsThatEverPassed() {
        when(scores.findScoredQuestions("smoke")).thenReturn(List.of(
                row("q-1", "答得不好", 40.0, Instant.parse("2026-10-15T10:00:00Z")),
                row("q-2", "第一次没过，第二次过了", 80.0, Instant.parse("2026-10-15T11:00:00Z")),
                row("q-2", "第一次没过，第二次过了", 30.0, Instant.parse("2026-10-14T11:00:00Z"))));

        MistakeBookView book = service.summary("smoke", 5);

        assertThat(book.mistakes()).extracting(MistakeBookView.Mistake::questionId).containsExactly("q-1");
        assertThat(book.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("追问不算错题：要重练的是原题")
    void ignoresFollowUpQuestions() {
        PracticeScoreEntity followUp = row("q-0-follow-1", "你说的优化具体改了什么？", 30.0, Instant.now());
        followUp.setQuestionCategory("follow_up");
        when(scores.findScoredQuestions("smoke")).thenReturn(List.of(followUp));

        assertThat(service.summary("smoke", 5).mistakes()).isEmpty();
    }

    @Test
    @DisplayName("最差的排最前，且 limit 只截显示条数、不截总数")
    void sortsWorstFirstAndKeepsTheTotal() {
        when(scores.findScoredQuestions("smoke")).thenReturn(List.of(
                row("q-1", "差一点", 70.0, Instant.parse("2026-10-15T10:00:00Z")),
                row("q-2", "很差", 20.0, Instant.parse("2026-10-15T09:00:00Z")),
                row("q-3", "最差", 5.0, Instant.parse("2026-10-15T08:00:00Z"))));

        MistakeBookView book = service.summary("smoke", 2);

        assertThat(book.mistakes()).extracting(MistakeBookView.Mistake::questionId).containsExactly("q-3", "q-2");
        assertThat(book.total()).isEqualTo(3);
        assertThat(book.scopeNote()).contains("75");
    }

    @Test
    @DisplayName("重练：建一场只有这道题的练习，沿用原场次的面试设置，并打开追问")
    void retryCreatesASingleQuestionSession() {
        PracticeScoreEntity row = row("q-1", "讲一个你优化过的接口", 52.0, Instant.now());
        row.setInterviewType("technical");
        row.setInterviewerStyle("direct");
        row.setDifficulty("advanced");
        when(scores.findFirstByOwnerIdAndQuestionIdAndQuestionTextIsNotNullOrderByEvaluatedAtDesc("smoke", "q-1"))
                .thenReturn(Optional.of(row));
        when(sessions.create(eq("smoke"), any())).thenReturn(null);

        service.retry("smoke", "q-1");

        ArgumentCaptor<PracticeSessionRequest> captor = ArgumentCaptor.forClass(PracticeSessionRequest.class);
        verify(sessions).create(eq("smoke"), captor.capture());
        PracticeSessionRequest request = captor.getValue();
        assertThat(request.questionCount()).isEqualTo(1);
        assertThat(request.questions()).singleElement()
                .satisfies(question -> {
                    assertThat(question.id()).isEqualTo("q-1");        // 题目 id 不变，重练成绩才会跟原题归到一起
                    assertThat(question.text()).isEqualTo("讲一个你优化过的接口");
                });
        assertThat(request.interviewType()).isEqualTo("technical");
        assertThat(request.interviewerStyle()).isEqualTo("direct");
        assertThat(request.difficulty()).isEqualTo("advanced");
        assertThat(request.followUpEnabled()).isTrue();
        assertThat(request.durationMinutes()).isEqualTo(4);
    }

    @Test
    @DisplayName("没有题面的旧记录不能重练：宁可 404，也不给一道空题")
    void refusesToRetryWithoutQuestionText() {
        when(scores.findFirstByOwnerIdAndQuestionIdAndQuestionTextIsNotNullOrderByEvaluatedAtDesc("smoke", "q-9"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retry("smoke", "q-9"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static PracticeScoreEntity row(String questionId, String text, double overall, Instant evaluatedAt) {
        PracticeScoreEntity entity = new PracticeScoreEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerId("smoke");
        entity.setSessionId(UUID.randomUUID());
        entity.setSessionCreatedAt(evaluatedAt);
        entity.setQuestionIndex(0);
        entity.setQuestionId(questionId);
        entity.setQuestionText(text);
        entity.setQuestionCategory("technical");
        entity.setRole("Java 后端工程师");
        entity.setDifficulty("standard");
        entity.setOverallScore(BigDecimal.valueOf(overall));
        entity.setRelevance(BigDecimal.valueOf(60));
        entity.setCompleteness(BigDecimal.valueOf(60));
        entity.setProfessionalism(BigDecimal.valueOf(60));
        entity.setStarStructure(BigDecimal.valueOf(40));
        entity.setLogic(BigDecimal.valueOf(60));
        entity.setEvaluatedAt(evaluatedAt);
        return entity;
    }
}
