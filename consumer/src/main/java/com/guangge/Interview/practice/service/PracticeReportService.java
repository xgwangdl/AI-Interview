package com.guangge.Interview.practice.service;

import com.guangge.Interview.analysis.EvaluationFormatException;
import com.guangge.Interview.analysis.EvaluationResult;
import com.guangge.Interview.analysis.EvaluationResultValidator;
import com.guangge.Interview.analysis.AnalysisJobEntity;
import com.guangge.Interview.analysis.AnalysisJobRepository;
import com.guangge.Interview.practice.dto.PracticeReportView;
import com.guangge.Interview.practice.dto.PracticeQuestion;
import com.guangge.Interview.practice.entity.PracticeAnswerEntity;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.media.MediaSignedUrlService;
import com.guangge.Interview.practice.repository.PracticeAnswerRepository;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

/** Assembles the per-question and overall report shown after a practice session. */
@Service
public class PracticeReportService {

    private static final String ANALYSIS_FAILED = "failed";

    private final PracticeSessionRepository sessions;
    private final PracticeAnswerRepository answers;
    private final AnalysisJobRepository jobs;
    private final EvaluationResultValidator validator;
    private final MediaSignedUrlService signedUrls;
    private final PracticeQuestionCatalog questions;

    public PracticeReportService(
            PracticeSessionRepository sessions,
            PracticeAnswerRepository answers,
            AnalysisJobRepository jobs,
            EvaluationResultValidator validator,
            MediaSignedUrlService signedUrls,
            PracticeQuestionCatalog questions) {
        this.sessions = sessions;
        this.answers = answers;
        this.jobs = jobs;
        this.validator = validator;
        this.signedUrls = signedUrls;
        this.questions = questions;
    }

    @Transactional(readOnly = true)
    public PracticeReportView report(String ownerId, UUID sessionId) {
        PracticeSessionEntity session = sessions.findByIdAndOwnerId(sessionId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Practice session not found"));
        List<PracticeAnswerEntity> sessionAnswers = answers.findAllBySessionIdOrderByQuestionIndexAsc(sessionId);
        List<PracticeQuestion> questionList = questionList(session);
        Map<String, String> followUpRoots = PracticeQuestionCatalog.followUpRoots(questionList);

        List<PracticeReportView.QuestionReport> questions = new ArrayList<>();
        List<EvaluationResult> evaluations = new ArrayList<>();
        int followUpAnswers = 0;
        for (PracticeAnswerEntity answer : sessionAnswers) {
            EvaluationResult evaluation = readEvaluation(answer);
            if (evaluation != null) evaluations.add(evaluation);
            String parentQuestionId = followUpRoots.get(answer.getQuestionId());
            if (parentQuestionId != null) followUpAnswers++;
            int index = PracticeQuestionCatalog.indexOf(questionList, answer.getQuestionId());
            questions.add(new PracticeReportView.QuestionReport(
                    answer.getId(),
                    answer.getQuestionIndex(),
                    answer.getQuestionId(),
                    questionText(session, answer.getQuestionId()),
                    answer.getDurationSeconds(),
                    answer.getTranscriptStatus(),
                    answer.getTranscriptText(),
                    answer.getEvaluationStatus(),
                    "/api/v1/answers/" + answer.getId() + "/media",
                    signedUrls.issue(answer.getId()).url(),
                    parentQuestionId != null,
                    parentQuestionId,
                    parentQuestionId == null ? null
                            : PracticeQuestionCatalog.indexOf(questionList, parentQuestionId),
                    index < 0 ? 0 : PracticeQuestionCatalog.followUpRoundOf(questionList, index),
                    evaluation));
        }

        PracticeReportView.AnalysisState analysis = analyze(jobs.findAllBySessionId(sessionId));

        return new PracticeReportView(
                session.getId(),
                session.getRole(),
                status(sessionAnswers.size(), evaluations.size(), analysis),
                session.getQuestionCount(),
                evaluations.size(),
                Instant.now(),
                summarize(evaluations, followUpAnswers),
                analysis,
                questions);
    }

    private EvaluationResult readEvaluation(PracticeAnswerEntity answer) {
        String json = answer.getEvaluationJson();
        if (json == null || json.isBlank()) return null;
        try {
            return validator.parseReport(json).result();
        } catch (EvaluationFormatException exception) {
            return null;
        }
    }

    /** 题目列表读不出来时不让整份报告垮掉：追问标记是增强信息，缺了照样出报告。 */
    private List<PracticeQuestion> questionList(PracticeSessionEntity session) {
        try {
            return questions.questions(session.getQuestionsJson());
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private String questionText(PracticeSessionEntity session, String questionId) {
        try {
            return questions.questionText(session.getQuestionsJson(), questionId);
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private static String status(int answerCount, int evaluatedCount, PracticeReportView.AnalysisState analysis) {
        if (answerCount == 0) return "empty";
        if (ANALYSIS_FAILED.equals(analysis.status())) return "analysis_failed";
        if (evaluatedCount == 0) return "awaiting_analysis";
        if (evaluatedCount == answerCount) return "ready";
        return "partial";
    }

    /**
     * 任务状态只存在库里：pending 表示还在队列中，failed 带脱敏后的失败原因。
     * 没有任务（例如迁移前的旧数据）时返回 none。
     */
    private static PracticeReportView.AnalysisState analyze(List<AnalysisJobEntity> sessionJobs) {
        if (sessionJobs.isEmpty()) {
            return new PracticeReportView.AnalysisState("none", 0, null);
        }
        int attempts = sessionJobs.stream().mapToInt(AnalysisJobEntity::getAttempts).max().orElse(0);
        for (AnalysisJobEntity job : sessionJobs) {
            if (ANALYSIS_FAILED.equals(job.getStatus())) {
                return new PracticeReportView.AnalysisState(ANALYSIS_FAILED, attempts, job.getLastError());
            }
        }
        boolean pending = sessionJobs.stream().anyMatch(job -> "pending".equals(job.getStatus()));
        if (pending) {
            return new PracticeReportView.AnalysisState("pending", attempts, null);
        }
        boolean processing = sessionJobs.stream().anyMatch(job -> "processing".equals(job.getStatus()));
        return new PracticeReportView.AnalysisState(processing ? "processing" : "completed", attempts, null);
    }

    private static PracticeReportView.ReportSummary summarize(List<EvaluationResult> evaluations, int followUpCount) {
        if (evaluations.isEmpty()) {
            return new PracticeReportView.ReportSummary(null, Map.of(), 0, 0, 0, 0, followUpCount, List.of());
        }
        Map<String, Double> dimensions = new LinkedHashMap<>();
        dimensions.put("relevance", average(evaluations, result -> result.scores().relevance()));
        dimensions.put("completeness", average(evaluations, result -> result.scores().completeness()));
        dimensions.put("professionalism", average(evaluations, result -> result.scores().professionalism()));
        dimensions.put("starStructure", average(evaluations, result -> result.scores().starStructure()));
        dimensions.put("logic", average(evaluations, result -> result.scores().logic()));

        List<String> suggestions = evaluations.stream()
                .flatMap(result -> result.improvementSuggestions().stream())
                .distinct()
                .toList();

        return new PracticeReportView.ReportSummary(
                average(evaluations, EvaluationResult::overallScore),
                dimensions,
                average(evaluations, result -> result.delivery().speechRateCharactersPerMinute()),
                (int) Math.round(evaluations.stream()
                        .mapToInt(result -> result.delivery().pauseCount()).average().orElse(0)),
                average(evaluations, result -> result.delivery().totalPauseSeconds()),
                (int) Math.round(evaluations.stream()
                        .mapToInt(result -> result.delivery().fillerWordCount()).average().orElse(0)),
                followUpCount,
                suggestions);
    }

    private static double average(List<EvaluationResult> evaluations, ToDoubleFunction<EvaluationResult> extractor) {
        double raw = evaluations.stream().mapToDouble(extractor).average().orElse(0);
        return Math.round(raw * 100.0) / 100.0;
    }
}
