package com.guangge.Interview.coding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.coding.dto.CodeQuestionView;
import com.guangge.Interview.coding.dto.CodingReportView;
import com.guangge.Interview.coding.dto.CodingSessionView;
import com.guangge.Interview.coding.dto.CodingSubmissionView;
import com.guangge.Interview.coding.dto.JudgeOutcome;
import com.guangge.Interview.coding.dto.Blank;
import com.guangge.Interview.coding.dto.CaseResult;
import com.guangge.Interview.coding.dto.ChoiceOption;
import com.guangge.Interview.coding.dto.TestCase;
import com.guangge.Interview.quota.ModelCallGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 笔试练习的生命周期：AI 出题建场、答题判分、报告、保留期清理。
 *
 * 与视频练习一样按 owner 隔离：不属于自己的会话一律 404（不泄露是否存在）。
 */
@Service
public class CodingSessionService {

    private static final Logger log = LoggerFactory.getLogger(CodingSessionService.class);
    private static final String STATUS_IN_PROGRESS = "in_progress";

    private final CodingSessionRepository sessions;
    private final CodeQuestionRepository questions;
    private final CodeSubmissionRepository submissions;
    private final CodingQuestionPlanService planner;
    private final CodeJudgeService judge;
    private final ModelCallGuard modelCalls;
    private final ObjectMapper objectMapper;
    private final int retentionDays;

    public CodingSessionService(CodingSessionRepository sessions,
                                CodeQuestionRepository questions,
                                CodeSubmissionRepository submissions,
                                CodingQuestionPlanService planner,
                                CodeJudgeService judge,
                                ModelCallGuard modelCalls,
                                ObjectMapper objectMapper,
                                @Value("${practice.coding.retention-days:7}") int retentionDays) {
        this.sessions = sessions;
        this.questions = questions;
        this.submissions = submissions;
        this.planner = planner;
        this.judge = judge;
        this.modelCalls = modelCalls;
        this.objectMapper = objectMapper;
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Transactional
    public CodingSessionView create(String ownerId, com.guangge.Interview.coding.dto.CodingPlanRequest request) {
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();

        CodingSessionEntity session = new CodingSessionEntity();
        session.setId(sessionId);
        session.setOwnerId(ownerId);
        session.setRole(request.role());
        session.setCompany(request.company());
        session.setJobDescription(request.jobDescription());
        session.setDifficulty(request.difficulty());
        session.setStatus(STATUS_IN_PROGRESS);
        session.setQuestionIdsJson("[]");
        session.setRetentionUntil(now.plus(retentionDays, ChronoUnit.DAYS));
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        // 先落会话：题目表对会话有外键，出题失败时整个事务回滚，不会留下空会话。
        sessions.save(session);

        // 出题是系统里最贵的一步（题目 + 自检参照解），额度不够就别开这个头。
        // 注意：出题内部可能重试若干轮，这里只记一次"用户操作"，
        // 所以它挡的是"能开多少场"，不是"到底发了几次模型请求"。
        List<CodeQuestionEntity> planned = modelCalls.call(ownerId, "笔试出题",
                () -> planner.generate(sessionId, request));
        questions.saveAll(planned);
        session.setQuestionIdsJson(writeIds(planned));
        session.setUpdatedAt(Instant.now());
        sessions.save(session);
        log.info("生成笔试会话 {}：{} 道题（owner={}）", sessionId, planned.size(), ownerId);
        return toView(session, planned, List.of());
    }

    @Transactional(readOnly = true)
    public List<CodingSessionView> list(String ownerId) {
        return sessions.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(session -> toView(session, questions.findAllBySessionIdOrderByPositionAsc(session.getId()),
                        submissions.findAllBySessionIdOrderByCreatedAtAsc(session.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CodingSessionView get(String ownerId, UUID sessionId) {
        CodingSessionEntity session = requireOwned(ownerId, sessionId);
        return toView(session, questions.findAllBySessionIdOrderByPositionAsc(sessionId),
                submissions.findAllBySessionIdOrderByCreatedAtAsc(sessionId));
    }

    /** 判分。persist=false 用于前端「运行」按钮：只跑可见样例，不写入历史。 */
    @Transactional
    public CodingSubmissionView judgeAnswer(String ownerId, UUID sessionId, UUID questionId,
                                            String answer, boolean visibleOnly) {
        CodingSessionEntity session = requireOwned(ownerId, sessionId);
        CodeQuestionEntity question = questions.findById(questionId)
                .filter(item -> item.getSessionId().equals(session.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));

        // 简答题靠模型按要点打分；编程 / 选择 / 填空只在沙箱里跑，不花模型的钱
        JudgeOutcome outcome = QuestionType.SHORT_ANSWER.id().equals(question.getType())
                ? modelCalls.call(ownerId, "简答判分", () -> judge.judge(question, answer, visibleOnly))
                : judge.judge(question, answer, visibleOnly);
        if (visibleOnly) {
            // 运行不落库：结果直接回给前端。
            return new CodingSubmissionView(null, questionId, outcome.status(), outcome.passedCases(),
                    outcome.totalCases(),
                    CodingSubmissionView.scoreOf(outcome.status(), outcome.passedCases(), outcome.totalCases(), outcome.review()),
                    outcome.runtimeMs(), outcome.stdout(), outcome.stderr(), outcome.compileOutput(),
                    outcome.cases(), outcome.review(), answer, Instant.now());
        }

        CodeSubmissionEntity entity = new CodeSubmissionEntity();
        entity.setId(UUID.randomUUID());
        entity.setSessionId(sessionId);
        entity.setQuestionId(questionId);
        entity.setOwnerId(ownerId);
        entity.setAnswerText(answer);
        entity.setStatus(outcome.status());
        entity.setPassedCases(outcome.passedCases());
        entity.setTotalCases(outcome.totalCases());
        entity.setRuntimeMs(outcome.runtimeMs());
        entity.setStdout(outcome.stdout());
        entity.setStderr(outcome.stderr());
        entity.setCompileOutput(outcome.compileOutput());
        entity.setCaseResultsJson(write(outcome.cases()));
        entity.setAiReviewJson(outcome.review() == null ? null : write(outcome.review()));
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        submissions.save(entity);
        return toSubmissionView(entity);
    }

    @Transactional(readOnly = true)
    public CodingReportView report(String ownerId, UUID sessionId) {
        CodingSessionEntity session = requireOwned(ownerId, sessionId);
        List<CodeQuestionEntity> questionList = questions.findAllBySessionIdOrderByPositionAsc(sessionId);
        Map<UUID, CodeSubmissionEntity> latest = latestByQuestion(sessionId);

        List<CodingReportView.QuestionReport> reports = new ArrayList<>();
        double scoreSum = 0;
        int scored = 0;
        int answered = 0;
        int passed = 0;
        for (CodeQuestionEntity question : questionList) {
            CodeSubmissionEntity submission = latest.get(question.getId());
            JudgeOutcome.ShortAnswerReview review = submission == null ? null : readReview(submission.getAiReviewJson());
            Double score = submission == null ? null : CodingSubmissionView.scoreOf(
                    submission.getStatus(), submission.getPassedCases(), submission.getTotalCases(), review);
            if (submission != null) answered++;
            if (JudgeOutcome.PASSED.equals(submission == null ? null : submission.getStatus())) passed++;
            if (score != null) {
                scoreSum += score;
                scored++;
            }
            reports.add(new CodingReportView.QuestionReport(
                    question.getId(), question.getPosition() + 1, question.getType(), question.getLanguage(),
                    question.getTitle(), question.getStatement(),
                    submission == null ? "unanswered" : submission.getStatus(),
                    submission == null ? 0 : submission.getPassedCases(),
                    submission == null ? 0 : submission.getTotalCases(),
                    score,
                    submission == null ? null : submission.getRuntimeMs(),
                    submission == null ? null : submission.getAnswerText(),
                    review,
                    submission == null ? null : submission.getCreatedAt()));
        }

        return new CodingReportView(sessionId, session.getRole(), session.getStatus(),
                questionList.size(), answered, passed,
                scored == 0 ? null : Math.round(scoreSum / scored * 10) / 10.0,
                Instant.now(), reports);
    }

    @Transactional
    public void delete(String ownerId, UUID sessionId) {
        CodingSessionEntity session = requireOwned(ownerId, sessionId);
        submissions.deleteAllBySessionId(sessionId);
        questions.deleteAllBySessionId(sessionId);
        sessions.delete(session);
    }

    /** 与视频练习一致的 7 天保留：到期的笔试连同题目与提交一起清掉。 */
    @Scheduled(fixedDelayString = "${practice.coding.cleanup-interval-ms:3600000}",
            initialDelayString = "${practice.coding.cleanup-initial-delay-ms:60000}")
    @Transactional
    public void deleteExpiredSessions() {
        List<CodingSessionEntity> expired = sessions.findAllByRetentionUntilBefore(Instant.now());
        for (CodingSessionEntity session : expired) {
            submissions.deleteAllBySessionId(session.getId());
            questions.deleteAllBySessionId(session.getId());
            sessions.delete(session);
        }
        if (!expired.isEmpty()) {
            log.info("清理过期笔试会话 {} 个", expired.size());
        }
    }

    // ---------- 视图组装 ----------

    private CodingSessionView toView(CodingSessionEntity session,
                                     List<CodeQuestionEntity> questionList,
                                     List<CodeSubmissionEntity> submissionList) {
        Map<UUID, CodeSubmissionEntity> latest = new HashMap<>();
        for (CodeSubmissionEntity submission : submissionList) {
            CodeSubmissionEntity current = latest.get(submission.getQuestionId());
            if (current == null || submission.getCreatedAt().isAfter(current.getCreatedAt())) {
                latest.put(submission.getQuestionId(), submission);
            }
        }
        List<CodingSessionView.SubmissionSummary> summaries = questionList.stream()
                .map(question -> Optional.ofNullable(latest.get(question.getId())))
                .flatMap(Optional::stream)
                .map(submission -> {
                    JudgeOutcome.ShortAnswerReview review = readReview(submission.getAiReviewJson());
                    return new CodingSessionView.SubmissionSummary(
                            submission.getQuestionId(), submission.getStatus(),
                            submission.getPassedCases(), submission.getTotalCases(),
                            CodingSubmissionView.scoreOf(submission.getStatus(), submission.getPassedCases(),
                                    submission.getTotalCases(), review),
                            submission.getCreatedAt());
                })
                .toList();
        return new CodingSessionView(session.getId(), session.getRole(), session.getCompany(),
                session.getDifficulty(), session.getStatus(), session.getCreatedAt(),
                session.getRetentionUntil(),
                questionList.stream().map(this::toQuestionView).toList(),
                summaries);
    }

    /**
     * 题目视图：**必须**剔除答案。
     * 填空只给 index 与 hint（不带 accepted），隐藏用例只给名字。
     */
    private CodeQuestionView toQuestionView(CodeQuestionEntity question) {
        List<Blank> blanks = read(question.getBlanksJson(), Blank.class);
        List<ChoiceOption> options = read(question.getOptionsJson(), ChoiceOption.class);
        List<TestCase> cases = read(question.getTestCasesJson(), TestCase.class);
        return new CodeQuestionView(
                question.getId(), question.getPosition() + 1, question.getType(), question.getLanguage(),
                question.getTitle(), question.getStatement(), question.getStarterCode(),
                blanks.stream().map(blank -> new CodeQuestionView.BlankView(blank.index(), blank.hint())).toList(),
                options,
                cases.stream().filter(item -> !item.hidden()).toList(),
                question.getSqlSetup(),
                question.getDifficulty(),
                question.getTags());
    }

    private CodingSubmissionView toSubmissionView(CodeSubmissionEntity entity) {
        List<CaseResult> cases = read(entity.getCaseResultsJson(), CaseResult.class);
        JudgeOutcome.ShortAnswerReview review = readReview(entity.getAiReviewJson());
        return new CodingSubmissionView(entity.getId(), entity.getQuestionId(), entity.getStatus(),
                entity.getPassedCases(), entity.getTotalCases(),
                CodingSubmissionView.scoreOf(entity.getStatus(), entity.getPassedCases(), entity.getTotalCases(), review),
                entity.getRuntimeMs(), entity.getStdout(), entity.getStderr(), entity.getCompileOutput(),
                cases, review, entity.getAnswerText(), entity.getCreatedAt());
    }

    private Map<UUID, CodeSubmissionEntity> latestByQuestion(UUID sessionId) {
        Map<UUID, CodeSubmissionEntity> latest = new HashMap<>();
        for (CodeSubmissionEntity submission : submissions.findAllBySessionIdOrderByCreatedAtAsc(sessionId)) {
            CodeSubmissionEntity current = latest.get(submission.getQuestionId());
            if (current == null || submission.getCreatedAt().isAfter(current.getCreatedAt())) {
                latest.put(submission.getQuestionId(), submission);
            }
        }
        return latest;
    }

    private CodingSessionEntity requireOwned(String ownerId, UUID sessionId) {
        return sessions.findByIdAndOwnerId(sessionId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coding session not found"));
    }

    private String writeIds(List<CodeQuestionEntity> questionList) {
        return write(questionList.stream().map(CodeQuestionEntity::getId).toList());
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("笔试数据序列化失败", exception);
        }
    }

    private <T> List<T> read(String json, Class<T> type) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (Exception exception) {
            log.warn("笔试数据解析失败：{}", exception.getMessage());
            return List.of();
        }
    }

    private JudgeOutcome.ShortAnswerReview readReview(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, JudgeOutcome.ShortAnswerReview.class);
        } catch (Exception exception) {
            return null;
        }
    }
}
