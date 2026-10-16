package com.guangge.Interview.practice.archive;

import com.guangge.Interview.practice.dto.PracticeArchiveView;
import com.guangge.Interview.practice.dto.PracticeReportView;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import com.guangge.Interview.practice.service.PracticeReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 档案聚合：趋势按时间正序、维度取窗口平均、短板只挑真的低的。 */
class PracticeArchiveServiceTest {

    private final PracticeSessionRepository sessions = mock(PracticeSessionRepository.class);
    private final PracticeReportService reports = mock(PracticeReportService.class);
    private final PracticeArchiveService service = new PracticeArchiveService(sessions, reports);

    private final Map<UUID, PracticeReportView> canned = new LinkedHashMap<>();

    @Test
    @DisplayName("趋势按旧→新排列，维度是窗口平均，短板只挑低于阈值的")
    void buildsTrendAndWeakest() {
        UUID newest = stubSession(3, "Java 后端工程师", 80.0, Map.of("relevance", 88.0, "starStructure", 62.0));
        UUID middle = stubSession(2, "Java 后端工程师", 70.0, Map.of("relevance", 82.0, "starStructure", 58.0));
        UUID oldest = stubSession(1, "Java 后端工程师", 60.0, Map.of("relevance", 78.0, "starStructure", 54.0));
        PracticeSessionEntity inProgress = session(UUID.randomUUID(), "进行中的不该进档案", "in_progress");
        when(sessions.findAllByOwnerIdOrderByCreatedAtDesc("smoke"))
                .thenReturn(List.of(sessionOf(newest, 3), sessionOf(middle, 2), sessionOf(oldest, 1), inProgress));
        when(reports.report(eq("smoke"), any())).thenAnswer(call -> canned.get(call.getArgument(1)));

        PracticeArchiveView view = service.summary("smoke", 5);

        assertThat(view.trend()).extracting(PracticeArchiveView.SessionPoint::overall)
                .containsExactly(60.0, 70.0, 80.0);
        assertThat(view.totals().scoredSessions()).isEqualTo(3);
        assertThat(view.totals().evaluatedAnswers()).isEqualTo(9);
        assertThat(view.enoughForTrend()).isTrue();
        assertThat(view.dimensions()).extracting(PracticeArchiveView.DimensionStat::key)
                .containsExactly("relevance", "starStructure");
        assertThat(view.dimensions()).extracting(PracticeArchiveView.DimensionStat::average)
                .containsExactly(82.67, 58.0);
        assertThat(view.weakest()).extracting(PracticeArchiveView.DimensionStat::key)
                .containsExactly("starStructure");
        assertThat(view.scopeNote()).contains("保留");
    }

    @Test
    @DisplayName("还没评分的场次不进档案：0 分和没有分数是两回事")
    void skipsSessionsWithoutScores() {
        PracticeSessionEntity scored = session(UUID.randomUUID(), "Java 后端工程师", "completed");
        PracticeSessionEntity notScored = session(UUID.randomUUID(), "Java 后端工程师", "completed");
        when(sessions.findAllByOwnerIdOrderByCreatedAtDesc("smoke")).thenReturn(List.of(notScored, scored));
        canned.put(scored.getId(), report(75.0, Map.of("relevance", 75.0)));
        canned.put(notScored.getId(), report(null, Map.of()));
        when(reports.report(eq("smoke"), any())).thenAnswer(call -> canned.get(call.getArgument(1)));

        PracticeArchiveView view = service.summary("smoke", 5);

        assertThat(view.trend()).hasSize(1);
        assertThat(view.totals().scoredSessions()).isEqualTo(1);
        assertThat(view.enoughForTrend()).isFalse();
    }

    @Test
    @DisplayName("成绩都不低时不给短板：不该硬挑一个出来说这里差")
    void reportsNoWeakestWhenEverythingIsFine() {
        UUID id = stubSession(1, "Java 后端工程师", 88.0, Map.of("relevance", 90.0, "logic", 86.0));
        when(sessions.findAllByOwnerIdOrderByCreatedAtDesc("smoke")).thenReturn(List.of(sessionOf(id, 1)));
        when(reports.report(eq("smoke"), any())).thenAnswer(call -> canned.get(call.getArgument(1)));

        PracticeArchiveView view = service.summary("smoke", 5);

        assertThat(view.weakest()).isEmpty();
        assertThat(view.enoughForTrend()).isFalse();
    }

    @Test
    @DisplayName("窗口大小有上下界：要大不给超出上限，要小按要的来")
    void capsTheWindow() {
        when(sessions.findAllByOwnerIdOrderByCreatedAtDesc("smoke")).thenReturn(List.of());

        assertThat(service.summary("smoke", 99).windowSize()).isEqualTo(PracticeArchiveService.MAX_WINDOW);
        assertThat(service.summary("smoke", 2).windowSize()).isEqualTo(2);
        assertThat(service.summary("smoke", null).windowSize()).isEqualTo(PracticeArchiveService.DEFAULT_WINDOW);
        assertThat(service.summary("smoke", 0).windowSize()).isEqualTo(1);
    }

    private UUID stubSession(int minutesAgo, String role, Double overall, Map<String, Double> dimensions) {
        UUID id = UUID.randomUUID();
        canned.put(id, report(overall, dimensions));
        return id;
    }

    private static PracticeReportView report(Double overall, Map<String, Double> dimensions) {
        return new PracticeReportView(UUID.randomUUID(), "Java 后端工程师", "ready", 3, 3, Instant.now(),
                new PracticeReportView.ReportSummary(overall, dimensions, 240, 2, 1.5, 3, List.of()),
                new PracticeReportView.AnalysisState("completed", 1, null),
                List.of());
    }

    private static PracticeSessionEntity session(UUID id, String role, String status) {
        PracticeSessionEntity entity = new PracticeSessionEntity();
        entity.setId(id);
        entity.setOwnerId("smoke");
        entity.setRole(role);
        entity.setStatus(status);
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    private static PracticeSessionEntity sessionOf(UUID id, int minutesAgo) {
        return session(id, "Java 后端工程师", "completed");
    }
}
