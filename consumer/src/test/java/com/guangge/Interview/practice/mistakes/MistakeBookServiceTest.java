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

        MistakeBookView book = service.summary("smoke", null, 0, 20);

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

        MistakeBookView book = service.summary("smoke", null, 0, 20);

        assertThat(book.mistakes()).extracting(MistakeBookView.Mistake::questionId).containsExactly("q-1");
        assertThat(book.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("追问不算错题：要重练的是原题")
    void ignoresFollowUpQuestions() {
        PracticeScoreEntity followUp = row("q-0-follow-1", "你说的优化具体改了什么？", 30.0, Instant.now());
        followUp.setQuestionCategory("follow_up");
        when(scores.findScoredQuestions("smoke")).thenReturn(List.of(followUp));

        assertThat(service.summary("smoke", null, 0, 20).mistakes()).isEmpty();
    }

    @Test
    @DisplayName("最差的排最前，且 limit 只截显示条数、不截总数")
    void sortsWorstFirstAndKeepsTheTotal() {
        when(scores.findScoredQuestions("smoke")).thenReturn(List.of(
                row("q-1", "差一点", 70.0, Instant.parse("2026-10-15T10:00:00Z")),
                row("q-2", "很差", 20.0, Instant.parse("2026-10-15T09:00:00Z")),
                row("q-3", "最差", 5.0, Instant.parse("2026-10-15T08:00:00Z"))));

        MistakeBookView book = service.summary("smoke", null, 0, 2);

        assertThat(book.mistakes()).extracting(MistakeBookView.Mistake::questionId).containsExactly("q-3", "q-2");
        assertThat(book.total()).isEqualTo(3);
        assertThat(book.scopeNote()).contains("75");
    }

    @Test
    @DisplayName("分页：offset/limit 在内存里切，总数始终是筛选后的全部数量")
    void pagesThroughTheMistakes() {
        when(scores.findScoredQuestions("smoke")).thenReturn(rows(25));

        MistakeBookView first = service.summary("smoke", null, 0, 20);
        MistakeBookView second = service.summary("smoke", null, 20, 20);
        MistakeBookView beyond = service.summary("smoke", null, 100, 20);

        assertThat(first.mistakes()).hasSize(20);
        assertThat(first.total()).isEqualTo(25);
        assertThat(first.offset()).isZero();
        // 第二页只剩 5 条，而不是把第一页再发一遍
        assertThat(second.mistakes()).hasSize(5);
        assertThat(second.total()).isEqualTo(25);
        assertThat(second.mistakes().get(0).questionId()).isNotEqualTo(first.mistakes().get(0).questionId());
        // 越界不是错误：给空列表，前端据此显示"这一页没有内容"
        assertThat(beyond.mistakes()).isEmpty();
        assertThat(beyond.total()).isEqualTo(25);
    }

    @Test
    @DisplayName("按岗位筛选：总数跟着筛选走，选项列表仍基于全部错题")
    void filtersByRole() {
        when(scores.findScoredQuestions("smoke")).thenReturn(List.of(
                row("q-1", "后端题", 40.0, Instant.parse("2026-10-15T10:00:00Z")),
                row("q-2", "另一道后端题", 50.0, Instant.parse("2026-10-15T11:00:00Z")),
                roleRow("q-3", "前端题", 60.0, "前端工程师")));

        MistakeBookView backend = service.summary("smoke", "Java 后端工程师", 0, 20);
        MistakeBookView frontend = service.summary("smoke", "前端工程师", 0, 20);
        MistakeBookView all = service.summary("smoke", null, 0, 20);

        assertThat(backend.total()).isEqualTo(2);
        assertThat(backend.mistakes()).extracting(MistakeBookView.Mistake::questionId).containsExactly("q-1", "q-2");
        assertThat(frontend.total()).isEqualTo(1);
        assertThat(frontend.role()).isEqualTo("前端工程师");
        // 筛选选项永远来自全部错题，否则选完岗位下拉里就只剩当前这一个
        assertThat(all.roles()).containsExactly(
                new MistakeBookView.RoleCount("Java 后端工程师", 2),
                new MistakeBookView.RoleCount("前端工程师", 1));
        // 不认识的岗位不会报错，只是没内容
        assertThat(service.summary("smoke", "不存在的岗位", 0, 20).mistakes()).isEmpty();
    }

    @Test
    @DisplayName("岗位为空的旧记录归到「未指定岗位」，不会在筛选里凭空消失")
    void groupsBlankRolesUnderAPlaceholder() {
        PracticeScoreEntity orphan = row("q-9", "没有岗位的旧记录", 30.0, Instant.now());
        orphan.setRole(null);
        when(scores.findScoredQuestions("smoke")).thenReturn(List.of(orphan));

        MistakeBookView book = service.summary("smoke", MistakeBookService.UNKNOWN_ROLE, 0, 20);

        assertThat(book.total()).isEqualTo(1);
        assertThat(book.mistakes().get(0).role()).isEqualTo("未指定岗位");
        assertThat(book.roles()).containsExactly(new MistakeBookView.RoleCount("未指定岗位", 1));
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
        PracticeScoreEntity entity = roleRow(questionId, text, overall, "Java 后端工程师");
        entity.setSessionCreatedAt(evaluatedAt);
        entity.setEvaluatedAt(evaluatedAt);
        return entity;
    }

    private static PracticeScoreEntity roleRow(String questionId, String text, double overall, String role) {
        PracticeScoreEntity entity = new PracticeScoreEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerId("smoke");
        entity.setSessionId(UUID.randomUUID());
        entity.setSessionCreatedAt(Instant.now());
        entity.setQuestionIndex(0);
        entity.setQuestionId(questionId);
        entity.setQuestionText(text);
        entity.setQuestionCategory("technical");
        entity.setRole(role);
        entity.setDifficulty("standard");
        entity.setOverallScore(BigDecimal.valueOf(overall));
        entity.setRelevance(BigDecimal.valueOf(60));
        entity.setCompleteness(BigDecimal.valueOf(60));
        entity.setProfessionalism(BigDecimal.valueOf(60));
        entity.setStarStructure(BigDecimal.valueOf(40));
        entity.setLogic(BigDecimal.valueOf(60));
        entity.setEvaluatedAt(Instant.now());
        return entity;
    }

    /** 造 N 道错题：分数递增，所以顺序是确定的（分页断言要靠它）。 */
    private static List<PracticeScoreEntity> rows(int count) {
        List<PracticeScoreEntity> rows = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) {
            rows.add(row("q-" + (index + 1), "第 " + (index + 1) + " 道错题", 10.0 + index,
                    Instant.parse("2026-10-15T10:00:00Z").plusSeconds(index * 60L)));
        }
        return rows;
    }
}
