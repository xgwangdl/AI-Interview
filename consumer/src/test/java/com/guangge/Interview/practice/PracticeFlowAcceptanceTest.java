package com.guangge.Interview.practice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.analysis.AnalysisJobEntity;
import com.guangge.Interview.analysis.AnalysisJobRepository;
import com.guangge.Interview.analysis.AnalysisJobService;
import com.guangge.Interview.analysis.AnalysisDlqRepository;
import com.guangge.Interview.analysis.AnalysisQueueMetrics;
import com.guangge.Interview.analysis.AsrProvider;
import com.guangge.Interview.analysis.EvaluationProvider;
import com.guangge.Interview.analysis.EvaluationProvenance;
import com.guangge.Interview.analysis.EvaluationOutcome;
import com.guangge.Interview.analysis.EvaluationRequest;
import com.guangge.Interview.analysis.EvaluationResult;
import com.guangge.Interview.analysis.EvaluationResultValidator;
import com.guangge.Interview.analysis.TranscriptResult;
import com.guangge.Interview.practice.dto.PracticeAnswerRequest;
import com.guangge.Interview.practice.dto.PracticeAnswerView;
import com.guangge.Interview.practice.dto.PracticeQuestion;
import com.guangge.Interview.practice.dto.PracticeReportView;
import com.guangge.Interview.practice.dto.PracticeSessionRequest;
import com.guangge.Interview.practice.dto.PracticeSessionView;
import com.guangge.Interview.practice.entity.PracticeAnswerEntity;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.media.MediaSignedUrlService;
import com.guangge.Interview.practice.repository.PracticeAnswerRepository;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import com.guangge.Interview.practice.service.PracticeQuestionCatalog;
import com.guangge.Interview.practice.service.PracticeReportService;
import com.guangge.Interview.practice.archive.PracticeScoreArchiveService;
import com.guangge.Interview.practice.service.PracticeSessionService;
import com.guangge.Interview.practice.storage.LocalMediaStorage;
import com.guangge.Interview.practice.storage.MediaStorage;
import com.guangge.Interview.quota.ModelCallGuard;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PLAN 的验收用例：五题模拟面试从录制一路走到报告，再验证删除后数据不可访问。
 * 仓储用内存实现替代数据库，媒体用真实的 {@link LocalMediaStorage} 落盘。
 */
class PracticeFlowAcceptanceTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private static final String RAW_MODEL_OUTPUT = "{\"overallScore\": 82.5, \"note\": \"raw-model-output\"}";
    private final Map<UUID, PracticeSessionEntity> sessionStore = new LinkedHashMap<>();
    private final Map<UUID, PracticeAnswerEntity> answerStore = new LinkedHashMap<>();
    private final Map<UUID, AnalysisJobEntity> jobStore = new LinkedHashMap<>();

    private PracticeSessionRepository sessions;
    private PracticeAnswerRepository answers;
    private AnalysisJobRepository jobs;
    private AnalysisDlqRepository dlq;
    private MediaStorage mediaStorage;
    private AnalysisQueueMetrics metrics;
    private PracticeSessionService sessionService;
    private AnalysisJobService jobService;
    private PracticeReportService reportService;
    private MediaSignedUrlService signedUrls;
    private PracticeScoreArchiveService scoreArchive;

    @BeforeEach
    void setUp() {
        sessions = mock(PracticeSessionRepository.class);
        answers = mock(PracticeAnswerRepository.class);
        jobs = mock(AnalysisJobRepository.class);
        dlq = mock(AnalysisDlqRepository.class);
        mediaStorage = new LocalMediaStorage(tempDir.toString());
        metrics = new AnalysisQueueMetrics(new SimpleMeterRegistry(), jobs, dlq);
        // 成绩档案用 mock：这个用例关心的是"分析链路能不能跑通"，档案落库有自己的单测
        scoreArchive = mock(PracticeScoreArchiveService.class);

        EvaluationResultValidator validator = new EvaluationResultValidator(objectMapper);
        PracticeQuestionCatalog catalog = new PracticeQuestionCatalog(objectMapper);
        signedUrls = new MediaSignedUrlService("acceptance-secret", 300);

        sessionService = new PracticeSessionService(sessions, answers, mediaStorage, objectMapper,
                scoreArchive, 7);
        jobService = new AnalysisJobService(jobs, dlq, sessions, answers, mediaStorage, transcribingAsr(),
                evaluatingProvider(), validator, catalog, metrics, scoreArchive,
                ModelCallGuard.inMemory(true, 10_000, 10), 3, 60_000, 1_000, 60_000);
        reportService = new PracticeReportService(sessions, answers, jobs, validator, signedUrls, catalog);

        stubRepositories();
    }

    @Test
    void runsFiveQuestionInterviewThenRemovesEveryTrace() throws IOException {
        PracticeSessionView created = sessionService.create("alice", request());
        assertEquals(5, created.questions().size());
        sessionService.start("alice", created.id());

        List<UUID> answerIds = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            PracticeAnswerView answer = sessionService.saveAnswer("alice", created.id(),
                    new PracticeAnswerRequest(index, "q-" + index, 60, index * 60));
            byte[] video = ("answer-" + index).getBytes(StandardCharsets.UTF_8);
            sessionService.storeAnswerMedia("alice", answer.id(), "video/webm", video.length,
                    new ByteArrayInputStream(video));
            answerIds.add(answer.id());
        }

        PracticeSessionView completed = sessionService.complete("alice", created.id());
        assertEquals("completed", completed.status());
        assertEquals(5, completed.answers().size());

        // 结束练习后每题都会进入分析队列，重复入队不会重复创建。
        assertEquals(5, jobService.enqueue(created.id()));
        assertEquals(0, jobService.enqueue(created.id()));

        jobService.processPending();

        assertTrue(answers.findById(answerIds.get(0)).orElseThrow().getEvaluationJson()
                        .contains("raw-model-output"),
                "每题的原始模型响应都要随报告一起保存");

        PracticeReportView report = reportService.report("alice", created.id());
        assertEquals("ready", report.status());
        assertEquals(5, report.evaluatedCount());
        assertEquals(82.5, report.summary().averageOverallScore(), 0.001);

        PracticeReportView.QuestionReport first = report.questions().get(0);
        // 转写内容来自真实落盘的录像字节，证明存储与转写链路是通的。
        assertEquals("转写：answer-0", first.transcriptText());
        assertNotNull(first.evaluation());
        assertEquals(1, first.evaluation().evidence().size());
        assertTrue(first.mediaSignedUrl().startsWith("/api/v1/media/" + first.answerId() + "?expires="));

        assertTrue(sessionService.getAnswerMedia(first.answerId()).resource().exists());
        assertDoesNotThrow(() -> signedUrls.verify(first.answerId(),
                expiresOf(first.mediaSignedUrl()), signatureOf(first.mediaSignedUrl())));

        sessionService.delete("alice", created.id());

        assertTrue(sessionService.list("alice").isEmpty());
        assertThrows(ResponseStatusException.class, () -> sessionService.get("alice", created.id()));
        assertThrows(ResponseStatusException.class, () -> reportService.report("alice", created.id()));
        for (UUID answerId : answerIds) {
            assertThrows(ResponseStatusException.class, () -> sessionService.getAnswerMedia(answerId));
            assertTrue(mediaStorage.find(answerId.toString()).isEmpty());
        }
        assertTrue(answers.findAllBySessionIdOrderByQuestionIndexAsc(created.id()).isEmpty());
    }

    private void stubRepositories() {
        when(sessions.save(any(PracticeSessionEntity.class))).thenAnswer(invocation -> {
            PracticeSessionEntity entity = invocation.getArgument(0);
            sessionStore.put(entity.getId(), entity);
            return entity;
        });
        when(sessions.findById(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(sessionStore.get(invocation.getArgument(0, UUID.class))));
        when(sessions.findByIdAndOwnerId(any(UUID.class), anyString())).thenAnswer(invocation -> {
            PracticeSessionEntity entity = sessionStore.get(invocation.getArgument(0, UUID.class));
            String owner = invocation.getArgument(1, String.class);
            return entity != null && owner.equals(entity.getOwnerId()) ? Optional.of(entity) : Optional.empty();
        });
        when(sessions.findAllByOwnerIdOrderByCreatedAtDesc(anyString())).thenAnswer(invocation -> {
            String owner = invocation.getArgument(0, String.class);
            return sessionStore.values().stream().filter(item -> owner.equals(item.getOwnerId())).toList();
        });
        doAnswer(invocation -> {
            sessionStore.remove(invocation.getArgument(0, PracticeSessionEntity.class).getId());
            return null;
        }).when(sessions).delete(any(PracticeSessionEntity.class));

        when(answers.save(any(PracticeAnswerEntity.class))).thenAnswer(invocation -> {
            PracticeAnswerEntity entity = invocation.getArgument(0);
            answerStore.put(entity.getId(), entity);
            return entity;
        });
        when(answers.findById(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(answerStore.get(invocation.getArgument(0, UUID.class))));
        when(answers.findBySessionIdAndQuestionIndex(any(UUID.class), anyInt())).thenAnswer(invocation -> {
            UUID sessionId = invocation.getArgument(0, UUID.class);
            int index = invocation.getArgument(1, Integer.class);
            return answerStore.values().stream()
                    .filter(item -> sessionId.equals(item.getSessionId()) && item.getQuestionIndex() == index)
                    .findFirst();
        });
        when(answers.findAllBySessionIdOrderByQuestionIndexAsc(any(UUID.class))).thenAnswer(invocation -> {
            UUID sessionId = invocation.getArgument(0, UUID.class);
            return answersOf(sessionId);
        });
        doAnswer(invocation -> {
            UUID sessionId = invocation.getArgument(0, UUID.class);
            answerStore.values().removeIf(item -> sessionId.equals(item.getSessionId()));
            return null;
        }).when(answers).deleteAllBySessionId(any(UUID.class));

        when(jobs.save(any(AnalysisJobEntity.class))).thenAnswer(invocation -> {
            AnalysisJobEntity entity = invocation.getArgument(0);
            jobStore.put(entity.getId(), entity);
            return entity;
        });
        when(jobs.findById(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(jobStore.get(invocation.getArgument(0, UUID.class))));
        when(jobs.findByAnswerId(any(UUID.class))).thenAnswer(invocation -> {
            UUID answerId = invocation.getArgument(0, UUID.class);
            return jobStore.values().stream().filter(job -> answerId.equals(job.getAnswerId())).findFirst();
        });
        when(jobs.findDueJobs(anyString(), any())).thenAnswer(invocation -> {
            String status = invocation.getArgument(0, String.class);
            return jobStore.values().stream().filter(job -> status.equals(job.getStatus())).toList();
        });
        // 认领成功：内存仓储里没有并发，条件更新总是命中。
        when(jobs.transitionStatus(any(UUID.class), anyString(), anyString(), any())).thenReturn(1);
    }

    private List<PracticeAnswerEntity> answersOf(UUID sessionId) {
        return answerStore.values().stream()
                .filter(item -> sessionId.equals(item.getSessionId()))
                .sorted(Comparator.comparingInt(PracticeAnswerEntity::getQuestionIndex))
                .toList();
    }

    private static PracticeSessionRequest request() {
        List<PracticeQuestion> questions = IntStream.range(0, 5)
                .mapToObj(index -> new PracticeQuestion("q-" + index, "第 " + index + " 题", "design"))
                .toList();
        return new PracticeSessionRequest("Java 后端", "Light", "负责订单系统", "resume.pdf",
                "technical", "standard", "balanced", 5, 20, false, questions);
    }

    private static AsrProvider transcribingAsr() {
        return (media, contentType) -> {
            try (var content = media.getInputStream()) {
                return TranscriptResult.ofText("转写：" + new String(content.readAllBytes(), StandardCharsets.UTF_8));
            }
        };
    }

    private static EvaluationProvider evaluatingProvider() {
        EvaluationResult result = new EvaluationResult(
                82.5,
                new EvaluationResult.DimensionScores(88.0, 80.0, 85.0, 75.0, 84.0),
                new EvaluationResult.DeliveryMetrics(210.5, 4, 7.5, 3),
                List.of("多用 STAR 结构描述项目"),
                "先讲背景，再讲任务与行动。",
                List.of(new EvaluationResult.Evidence("relevance", "answer", 0.0, 5.0)));
        return new EvaluationProvider() {
            @Override
            public EvaluationOutcome evaluate(EvaluationRequest request) {
                return new EvaluationOutcome(result, RAW_MODEL_OUTPUT);
            }

            @Override
            public EvaluationProvenance provenance() {
                return new EvaluationProvenance("qwen-max", "qwen-max", "practice-evaluation-v1");
            }
        };
    }

    private static long expiresOf(String signedUrl) {
        int start = signedUrl.indexOf("expires=") + "expires=".length();
        return Long.parseLong(signedUrl.substring(start, signedUrl.indexOf('&')));
    }

    private static String signatureOf(String signedUrl) {
        return signedUrl.substring(signedUrl.indexOf("signature=") + "signature=".length());
    }
}
