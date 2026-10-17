package com.guangge.Interview.practice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.analysis.AnalysisJobEntity;
import com.guangge.Interview.analysis.AnalysisJobRepository;
import com.guangge.Interview.analysis.EvaluationReport;
import com.guangge.Interview.analysis.EvaluationResult;
import com.guangge.Interview.analysis.EvaluationResultValidator;
import com.guangge.Interview.practice.dto.PracticeReportView;
import com.guangge.Interview.practice.entity.PracticeAnswerEntity;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.media.MediaSignedUrlService;
import com.guangge.Interview.practice.repository.PracticeAnswerRepository;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PracticeReportServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final EvaluationResultValidator validator = new EvaluationResultValidator(objectMapper);

    @Test
    void aggregatesEvaluatedAnswersIntoAnOverallReport() {
        UUID sessionId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        PracticeSessionEntity session = session(sessionId, 1);
        PracticeAnswerEntity answer = answer(sessionId, answerId);
        answer.setEvaluationJson(validator.write(new EvaluationReport("qwen-max", "qwen-max",
                "practice-evaluation-v1", Instant.now(), "", evaluation())));
        answer.setEvaluationStatus("completed");

        PracticeReportView report = buildReport(session, List.of(answer));

        assertEquals("ready", report.status());
        assertEquals(1, report.evaluatedCount());
        assertEquals(82.5, report.summary().averageOverallScore(), 0.001);
        assertEquals(88.0, report.summary().averageDimensionScores().get("relevance"), 0.001);
        assertEquals(4, report.summary().totalPauseCount());
        assertEquals("讲一个你主导的重构", report.questions().get(0).questionText());
        assertEquals("/api/v1/answers/" + answerId + "/media", report.questions().get(0).mediaUrl());
        assertTrue(report.questions().get(0).mediaSignedUrl()
                .startsWith("/api/v1/media/" + answerId + "?expires="));
        assertNotNull(report.questions().get(0).evaluation());
    }

    @Test
    void marksFollowUpAnswersAsFollowUps() {
        UUID sessionId = UUID.randomUUID();
        PracticeSessionEntity session = session(sessionId, 2);
        session.setQuestionsJson("""
                [{"id":"q-1","text":"讲一个你主导的重构","category":"design"},
                 {"id":"q-0-follow-1","text":"重构具体改了什么？","category":"follow_up"}]
                """);
        PracticeAnswerEntity first = answer(sessionId, UUID.randomUUID());
        PracticeAnswerEntity followUp = answer(sessionId, UUID.randomUUID());
        followUp.setQuestionIndex(1);
        followUp.setQuestionId("q-0-follow-1");

        PracticeReportView report = buildReport(session, List.of(first, followUp));

        assertTrue(report.questions().get(0).followUp() == false);
        PracticeReportView.QuestionReport marked = report.questions().get(1);
        assertTrue(marked.followUp());
        assertEquals("q-1", marked.parentQuestionId());
        assertEquals(0, marked.parentQuestionIndex());
        assertEquals(1, marked.followUpRound());
        assertEquals("重构具体改了什么？", marked.questionText());
        // 报告里要能说出"这几道题其实是追问"，否则用户会以为平白多考了几题
        assertEquals(1, report.summary().followUpCount());
    }

    @Test
    void reportsAwaitingAnalysisWhenNothingHasBeenEvaluatedYet() {
        UUID sessionId = UUID.randomUUID();
        PracticeSessionEntity session = session(sessionId, 1);
        PracticeAnswerEntity answer = answer(sessionId, UUID.randomUUID());

        PracticeReportView report = buildReport(session, List.of(answer));

        assertEquals("awaiting_analysis", report.status());
        assertEquals(0, report.evaluatedCount());
        assertNull(report.summary().averageOverallScore());
        assertNull(report.questions().get(0).evaluation());
    }

    @Test
    void hidesReportsForSessionsOwnedByAnotherUser() {
        UUID sessionId = UUID.randomUUID();
        PracticeSessionRepository sessions = mock(PracticeSessionRepository.class);
        PracticeAnswerRepository answers = mock(PracticeAnswerRepository.class);
        AnalysisJobRepository jobs = mock(AnalysisJobRepository.class);
        when(sessions.findByIdAndOwnerId(sessionId, "alice")).thenReturn(Optional.empty());

        PracticeReportService service = new PracticeReportService(
                sessions, answers, jobs, validator, signedUrls(), new PracticeQuestionCatalog(objectMapper));

        assertThrows(ResponseStatusException.class, () -> service.report("alice", sessionId));
    }

    @Test
    void serialisesEveryFieldTheWebReportViewReads() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        PracticeSessionEntity session = session(sessionId, 1);
        PracticeAnswerEntity answer = answer(sessionId, answerId);
        answer.setEvaluationJson(validator.write(new EvaluationReport("qwen-max", "qwen-max",
                "practice-evaluation-v1", Instant.now(), "", evaluation())));
        answer.setEvaluationStatus("completed");

        String json = objectMapper.writeValueAsString(buildReport(session, List.of(answer)));

        for (String field : List.of(
                "sessionId", "status", "questionCount", "evaluatedCount", "summary",
                "averageOverallScore", "averageDimensionScores", "speechRateCharactersPerMinute",
                "totalPauseCount", "totalFillerWordCount", "improvementSuggestions", "questions",
                "answerId", "questionIndex", "questionText", "transcriptText", "mediaUrl",
                "mediaSignedUrl", "evaluation", "overallScore", "scores", "relevance",
                "starStructure", "delivery", "fillerWordCount", "evidence", "startSeconds",
                "endSeconds", "sampleAnswer", "analysis", "attempts", "error",
                "followUp", "parentQuestionIndex", "followUpRound", "followUpCount")) {
            assertTrue(json.contains("\"" + field + "\""), "report JSON is missing field: " + field);
        }
    }

    @Test
    void surfacesAnalysisFailureInsteadOfWaitingForever() {
        UUID sessionId = UUID.randomUUID();
        PracticeSessionEntity session = session(sessionId, 1);
        PracticeAnswerEntity answer = answer(sessionId, UUID.randomUUID());
        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setId(UUID.randomUUID());
        job.setSessionId(sessionId);
        job.setAnswerId(answer.getId());
        job.setStatus("failed");
        job.setAttempts(3);
        job.setLastError("FFmpeg 转换失败，退出码: 1");

        PracticeReportView report = buildReport(session, List.of(answer), List.of(job));

        assertEquals("analysis_failed", report.status());
        assertEquals("failed", report.analysis().status());
        assertEquals(3, report.analysis().attempts());
        assertEquals("FFmpeg 转换失败，退出码: 1", report.analysis().error());
    }

    @Test
    void reportsPendingWhileJobsAreStillQueued() {
        UUID sessionId = UUID.randomUUID();
        PracticeSessionEntity session = session(sessionId, 1);
        PracticeAnswerEntity answer = answer(sessionId, UUID.randomUUID());
        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setId(UUID.randomUUID());
        job.setSessionId(sessionId);
        job.setAnswerId(answer.getId());
        job.setStatus("pending");
        job.setAttempts(1);

        PracticeReportView report = buildReport(session, List.of(answer), List.of(job));

        assertEquals("awaiting_analysis", report.status());
        assertEquals("pending", report.analysis().status());
        assertNull(report.analysis().error());
    }

    private PracticeReportView buildReport(PracticeSessionEntity session, List<PracticeAnswerEntity> answers) {
        return buildReport(session, answers, List.of());
    }

    private PracticeReportView buildReport(PracticeSessionEntity session, List<PracticeAnswerEntity> answers,
                                           List<AnalysisJobEntity> sessionJobs) {
        PracticeSessionRepository sessions = mock(PracticeSessionRepository.class);
        PracticeAnswerRepository answerRepository = mock(PracticeAnswerRepository.class);
        AnalysisJobRepository jobRepository = mock(AnalysisJobRepository.class);
        when(sessions.findByIdAndOwnerId(session.getId(), "alice")).thenReturn(Optional.of(session));
        when(answerRepository.findAllBySessionIdOrderByQuestionIndexAscAttemptAsc(session.getId())).thenReturn(answers);
        when(jobRepository.findAllBySessionId(session.getId())).thenReturn(sessionJobs);
        return new PracticeReportService(
                sessions, answerRepository, jobRepository, validator, signedUrls(),
                new PracticeQuestionCatalog(objectMapper))
                .report("alice", session.getId());
    }

    private static MediaSignedUrlService signedUrls() {
        return new MediaSignedUrlService("test-secret", 300);
    }

    private static PracticeSessionEntity session(UUID id, int questionCount) {
        PracticeSessionEntity session = new PracticeSessionEntity();
        session.setId(id);
        session.setRole("Java 后端");
        session.setQuestionCount(questionCount);
        session.setQuestionsJson("[{\"id\":\"q-1\",\"text\":\"讲一个你主导的重构\",\"category\":\"design\"}]");
        session.setStatus("completed");
        return session;
    }

    private static PracticeAnswerEntity answer(UUID sessionId, UUID answerId) {
        PracticeAnswerEntity answer = new PracticeAnswerEntity();
        answer.setId(answerId);
        answer.setSessionId(sessionId);
        answer.setQuestionIndex(0);
        answer.setQuestionId("q-1");
        answer.setDurationSeconds(120);
        answer.setTranscriptStatus("completed");
        answer.setTranscriptText("我主导了订单系统的重构");
        answer.setEvaluationStatus("awaiting_provider");
        return answer;
    }

    private static EvaluationResult evaluation() {
        return new EvaluationResult(
                82.5,
                new EvaluationResult.DimensionScores(88.0, 80.0, 85.0, 75.0, 84.0),
                new EvaluationResult.DeliveryMetrics(210.5, 4, 7.5, 3),
                List.of("多用 STAR 结构描述项目"),
                "先讲背景，再讲任务与行动。",
                List.of(new EvaluationResult.Evidence("relevance", "我主导了订单系统的重构", 0.0, 5.0)));
    }
}
