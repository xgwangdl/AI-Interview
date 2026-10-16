package com.guangge.Interview.practice.archive;

import com.guangge.Interview.practice.dto.PracticeArchiveView;
import com.guangge.Interview.practice.entity.PracticeScoreEntity;
import com.guangge.Interview.practice.repository.PracticeScoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 档案聚合：数据源是长期保留的成绩表（场次过期也不影响），
 * 所以这里不依赖任何场次/转写数据。
 */
class PracticeArchiveServiceTest {

    private final PracticeScoreRepository scores = mock(PracticeScoreRepository.class);
    private final PracticeArchiveService service = new PracticeArchiveService(scores);

    private final List<PracticeScoreEntity> rows = new ArrayList<>();

    @Test
    @DisplayName("趋势按旧→新排列，维度取窗口平均，短板只挑低于阈值的")
    void buildsTrendAndWeakest() {
        // 3 天前 → 1 天前：成绩从 60 涨到 80，趋势图应当按旧→新排列
        UUID oldest = session(3, "Java 后端工程师", 60.0, 78.0, 70.0, 74.0, 54.0, 66.0);
        UUID middle = session(2, "Java 后端工程师", 70.0, 82.0, 76.0, 80.0, 58.0, 70.0);
        UUID newest = session(1, "Java 后端工程师", 80.0, 88.0, 82.0, 86.0, 62.0, 74.0);
        stubRecent(List.of(newest, middle, oldest));

        PracticeArchiveView view = service.summary("smoke", 5);

        assertThat(view.trend()).extracting(PracticeArchiveView.SessionPoint::overall)
                .containsExactly(60.0, 70.0, 80.0);
        assertThat(view.totals().scoredSessions()).isEqualTo(3);
        assertThat(view.totals().evaluatedAnswers()).isEqualTo(3);
        assertThat(view.enoughForTrend()).isTrue();
        assertThat(view.dimensions()).extracting(PracticeArchiveView.DimensionStat::key)
                .containsExactly("relevance", "completeness", "professionalism", "starStructure", "logic");
        assertThat(view.dimensions().get(3).average()).isEqualTo(58.0);
        assertThat(view.weakest()).extracting(PracticeArchiveView.DimensionStat::key)
                .containsExactly("starStructure", "logic");
        assertThat(view.scopeNote()).contains("长期保留").contains("3 场");
    }

    @Test
    @DisplayName("一场多题：按题平均，不会因为回答多就把这一场算得更重")
    void averagesAnswersWithinASession() {
        UUID sessionId = UUID.randomUUID();
        Instant created = Instant.now().minus(1, ChronoUnit.DAYS);
        row(sessionId, created, 0, 90.0, 90.0, 90.0, 90.0, 90.0, 90.0);
        row(sessionId, created, 1, 60.0, 60.0, 60.0, 60.0, 60.0, 60.0);
        stubRecent(List.of(sessionId));

        PracticeArchiveView view = service.summary("smoke", 5);

        assertThat(view.trend()).hasSize(1);
        assertThat(view.trend().get(0).overall()).isEqualTo(75.0);
        assertThat(view.totals().evaluatedAnswers()).isEqualTo(2);
    }

    @Test
    @DisplayName("没有分数就没有档案：不报错，给空壳")
    void returnsEmptyArchive() {
        when(scores.findRecentSessionIds(eq("smoke"), any(PageRequest.class))).thenReturn(List.of());

        PracticeArchiveView view = service.summary("smoke", 5);

        assertThat(view.trend()).isEmpty();
        assertThat(view.dimensions()).isEmpty();
        assertThat(view.weakest()).isEmpty();
        assertThat(view.enoughForTrend()).isFalse();
    }

    @Test
    @DisplayName("成绩都在 75 以上时不给短板：不该硬挑一个出来说这里差")
    void reportsNoWeakestWhenEverythingIsFine() {
        UUID id = session(1, "Java 后端工程师", 88.0, 90.0, 88.0, 86.0, 92.0, 86.0);
        stubRecent(List.of(id));

        PracticeArchiveView view = service.summary("smoke", 5);

        assertThat(view.weakest()).isEmpty();
    }

    @Test
    @DisplayName("窗口有上下界，且真的传给查询")
    void capsTheWindow() {
        when(scores.findRecentSessionIds(eq("smoke"), any(PageRequest.class))).thenReturn(List.of());

        assertThat(service.summary("smoke", 99).windowSize()).isEqualTo(PracticeArchiveService.MAX_WINDOW);
        assertThat(service.summary("smoke", 2).windowSize()).isEqualTo(2);
        assertThat(service.summary("smoke", null).windowSize()).isEqualTo(PracticeArchiveService.DEFAULT_WINDOW);
        assertThat(service.summary("smoke", 0).windowSize()).isEqualTo(1);
    }

    private void stubRecent(List<UUID> sessionIds) {
        when(scores.findRecentSessionIds(eq("smoke"), any(PageRequest.class))).thenReturn(sessionIds);
        when(scores.findAllByOwnerIdAndSessionIdIn(eq("smoke"), any())).thenReturn(rows);
    }

    private UUID session(int daysAgo, String role, double overall, double relevance, double completeness,
                         double professionalism, double star, double logic) {
        UUID id = UUID.randomUUID();
        Instant created = Instant.now().minus(daysAgo, ChronoUnit.DAYS);
        row(id, created, 0, overall, relevance, completeness, professionalism, star, logic);
        return id;
    }

    private void row(UUID sessionId, Instant created, int index, double overall, double relevance,
                     double completeness, double professionalism, double star, double logic) {
        PracticeScoreEntity entity = new PracticeScoreEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerId("smoke");
        entity.setSessionId(sessionId);
        entity.setSessionCreatedAt(created);
        entity.setQuestionIndex(index);
        entity.setQuestionId("q-" + index);
        entity.setRole("Java 后端工程师");
        entity.setOverallScore(BigDecimal.valueOf(overall));
        entity.setRelevance(BigDecimal.valueOf(relevance));
        entity.setCompleteness(BigDecimal.valueOf(completeness));
        entity.setProfessionalism(BigDecimal.valueOf(professionalism));
        entity.setStarStructure(BigDecimal.valueOf(star));
        entity.setLogic(BigDecimal.valueOf(logic));
        entity.setEvaluatedAt(Instant.now());
        rows.add(entity);
    }
}
