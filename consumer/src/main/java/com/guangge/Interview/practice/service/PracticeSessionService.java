package com.guangge.Interview.practice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.practice.dto.PracticeAnswerRequest;
import com.guangge.Interview.practice.dto.PracticeAnswerView;
import com.guangge.Interview.practice.dto.PracticeQuestion;
import com.guangge.Interview.practice.dto.PracticeSessionRequest;
import com.guangge.Interview.practice.dto.PracticeSessionView;
import com.guangge.Interview.practice.entity.PracticeAnswerEntity;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.repository.PracticeAnswerRepository;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import com.guangge.Interview.practice.storage.MediaStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PracticeSessionService {
    private static final TypeReference<List<PracticeQuestion>> QUESTIONS_TYPE = new TypeReference<>() { };

    private final PracticeSessionRepository sessions;
    private final PracticeAnswerRepository answers;
    private final MediaStorage mediaStorage;
    private final ObjectMapper objectMapper;
    private final com.guangge.Interview.practice.archive.PracticeScoreArchiveService scoreArchive;
    private final int retentionDays;

    public PracticeSessionService(
            PracticeSessionRepository sessions,
            PracticeAnswerRepository answers,
            MediaStorage mediaStorage,
            ObjectMapper objectMapper,
            com.guangge.Interview.practice.archive.PracticeScoreArchiveService scoreArchive,
            @Value("${practice.media.retention-days:7}") int retentionDays) {
        this.sessions = sessions;
        this.answers = answers;
        this.mediaStorage = mediaStorage;
        this.objectMapper = objectMapper;
        this.scoreArchive = scoreArchive;
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Transactional
    public PracticeSessionView create(String ownerId, PracticeSessionRequest request) {
        if (request.questions().size() != request.questionCount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question count does not match questions");
        }
        Instant now = Instant.now();
        PracticeSessionEntity session = new PracticeSessionEntity();
        session.setId(UUID.randomUUID());
        session.setOwnerId(ownerId);
        session.setGoalId(request.goalId());
        session.setRole(request.role().trim());
        session.setCompany(trimToNull(request.company()));
        session.setJobDescription(trimToNull(request.jobDescription()));
        session.setResumeName(trimToNull(request.resumeName()));
        session.setInterviewType(request.interviewType());
        session.setDifficulty(request.difficulty());
        session.setInterviewerStyle(request.interviewerStyle());
        session.setQuestionCount(request.questionCount());
        session.setDurationMinutes(request.durationMinutes());
        session.setFollowUpEnabled(request.followUpEnabled());
        session.setMode(normalizeMode(request.mode()));
        session.setQuestionsJson(writeQuestions(request.questions()));
        session.setStatus("in_progress");
        session.setCreatedAt(now);
        session.setRetentionUntil(now.plusSeconds(retentionDays * 86400L));
        return toView(sessions.save(session), List.of());
    }

    @Transactional(readOnly = true)
    public List<PracticeSessionView> list(String ownerId) {
        return sessions.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(this::toViewWithAnswers)
                .toList();
    }

    /** 把一场由"错题重练"创建的练习挂到目标上（用于每日计划里的错题项）。 */
    @Transactional
    public PracticeSessionView linkGoal(String ownerId, UUID sessionId, UUID goalId) {
        PracticeSessionEntity session = requireOwnedSession(ownerId, sessionId);
        session.setGoalId(goalId);
        return toView(sessions.save(session), answerViews(sessionId));
    }

    @Transactional(readOnly = true)
    public List<PracticeSessionView> listByGoal(String ownerId, UUID goalId) {
        return sessions.findAllByOwnerIdAndGoalIdOrderByCreatedAtDesc(ownerId, goalId).stream()
                .map(this::toViewWithAnswers)
                .toList();
    }

    @Transactional(readOnly = true)
    public PracticeSessionView get(String ownerId, UUID id) {
        return toViewWithAnswers(requireOwnedSession(ownerId, id));
    }

    @Transactional
    public PracticeSessionView start(String ownerId, UUID id) {
        PracticeSessionEntity session = requireOwnedSession(ownerId, id);
        if (session.getStartedAt() == null) session.setStartedAt(Instant.now());
        if (!"completed".equals(session.getStatus())) session.setStatus("in_progress");
        return toView(sessions.save(session), answerViews(session.getId()));
    }

    /** 连续面试允许暂停一次；逐题模式没有这个动作。 */
    @Transactional
    public PracticeSessionView pause(String ownerId, UUID id) {
        PracticeSessionEntity session = requireOwnedSession(ownerId, id);
        if (!"continuous".equals(session.getMode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只有连续面试可以暂停");
        }
        if (session.getPausedCount() >= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "连续面试只能暂停一次");
        }
        session.setPausedCount(session.getPausedCount() + 1);
        return toView(sessions.save(session), answerViews(id));
    }

    @Transactional
    public PracticeAnswerView saveAnswer(String ownerId, UUID sessionId, PracticeAnswerRequest request) {
        PracticeSessionEntity session = requireOwnedSession(ownerId, sessionId);
        if ("completed".equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Completed practice sessions cannot be changed");
        }
        if (request.questionIndex() >= session.getQuestionCount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question index is outside this session");
        }
        List<PracticeQuestion> questions = readQuestions(session.getQuestionsJson());
        if (!questions.get(request.questionIndex()).id().equals(request.questionId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question does not belong to this position in the session");
        }

        PracticeAnswerEntity answer = answers.findBySessionIdAndQuestionIndex(sessionId, request.questionIndex())
                .orElseGet(() -> {
                    PracticeAnswerEntity created = new PracticeAnswerEntity();
                    created.setId(UUID.randomUUID());
                    created.setSessionId(sessionId);
                    created.setQuestionIndex(request.questionIndex());
                    created.setCreatedAt(Instant.now());
                    created.setTranscriptStatus("awaiting_provider");
                    created.setEvaluationStatus("awaiting_provider");
                    return created;
                });
        answer.setQuestionId(request.questionId());
        answer.setDurationSeconds(request.durationSeconds());
        answer.setStartedAtOffsetSeconds(request.startedAtOffsetSeconds());
        return toAnswerView(answers.save(answer));
    }

    @Transactional
    public PracticeAnswerView storeAnswerMedia(String ownerId, UUID answerId, String contentType, long size, InputStream content) throws IOException {
        PracticeAnswerEntity answer = requireOwnedAnswer(ownerId, answerId);
        if (contentType == null || !contentType.toLowerCase().startsWith("video/")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "A video file is required");
        }
        String key = answer.getId().toString();
        mediaStorage.store(key, content, size);
        answer.setMediaKey(key);
        answer.setMediaContentType(contentType);
        answer.setMediaSizeBytes(size);
        return toAnswerView(answers.save(answer));
    }

    @Transactional(readOnly = true)
    public MediaFile getAnswerMedia(String ownerId, UUID answerId) {
        requireOwnedAnswer(ownerId, answerId);
        return getAnswerMedia(answerId);
    }

    /**
     * 分片上传的归属校验入口：确认作答存在且属于当前用户（否则 404）。
     * 分片本身由 {@code ChunkedUploadService} 落到媒体存储，这里只负责授权。
     */
    @Transactional(readOnly = true)
    public void assertOwnedAnswer(String ownerId, UUID answerId) {
        requireOwnedAnswer(ownerId, answerId);
    }

    /** Reads media by answer id only; callers must have authorised the request first. */
    @Transactional(readOnly = true)
    public MediaFile getAnswerMedia(UUID answerId) {
        PracticeAnswerEntity answer = answers.findById(answerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Practice answer not found"));
        if (answer.getMediaKey() == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found");
        Resource media = mediaStorage.find(answer.getMediaKey())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));
        return new MediaFile(media, answer.getMediaContentType());
    }

    @Transactional
    public PracticeSessionView complete(String ownerId, UUID id) {
        PracticeSessionEntity session = requireOwnedSession(ownerId, id);
        List<PracticeAnswerEntity> sessionAnswers = answers.findAllBySessionIdOrderByQuestionIndexAsc(id);
        if (sessionAnswers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Record at least one answer before completing");
        }
        if (sessionAnswers.stream().anyMatch(answer -> answer.getMediaKey() == null)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Wait for every recorded answer to finish uploading");
        }
        session.setStatus("completed");
        session.setCompletedAt(Instant.now());
        return toView(sessions.save(session), answerViews(id));
    }

    /** 单题完成：录像必须已经上传，否则没什么可分析的。 */
    @Transactional(readOnly = true)
    public PracticeAnswerView completeAnswer(String ownerId, UUID answerId) {
        PracticeAnswerEntity answer = requireOwnedAnswer(ownerId, answerId);
        if (answer.getMediaKey() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload the answer video before completing it");
        }
        return toAnswerView(answer);
    }

    @Transactional
    public void delete(String ownerId, UUID id) throws IOException {
        PracticeSessionEntity session = requireOwnedSession(ownerId, id);
        // 用户主动删：连长期成绩档案一起删（"删除"就该是删除）
        deleteSession(session, true);
    }

    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    public void deleteExpiredSessions() {
        for (PracticeSessionEntity session : sessions.findAllByRetentionUntilBefore(Instant.now())) {
            try {
                // 到期清理：只删录像与练习本身，成绩档案留着 —— 这正是训练档案能长期看趋势的原因
                deleteSession(session, false);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to remove expired practice media", exception);
            }
        }
    }

    private void deleteSession(PracticeSessionEntity session, boolean forgetScores) throws IOException {
        List<PracticeAnswerEntity> sessionAnswers = answers.findAllBySessionIdOrderByQuestionIndexAsc(session.getId());
        for (PracticeAnswerEntity answer : sessionAnswers) {
            if (answer.getMediaKey() != null) mediaStorage.delete(answer.getMediaKey());
        }
        answers.deleteAllBySessionId(session.getId());
        if (forgetScores) scoreArchive.forgetSession(session.getId());
        sessions.delete(session);
    }

    private PracticeSessionEntity requireOwnedSession(String ownerId, UUID id) {
        return sessions.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Practice session not found"));
    }

    private PracticeAnswerEntity requireOwnedAnswer(String ownerId, UUID answerId) {
        PracticeAnswerEntity answer = answers.findById(answerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Practice answer not found"));
        requireOwnedSession(ownerId, answer.getSessionId());
        return answer;
    }

    private PracticeSessionView toViewWithAnswers(PracticeSessionEntity session) {
        return toView(session, answerViews(session.getId()));
    }

    /**
     * 追加一道追问题目（见 {@link com.guangge.Interview.practice.followup.PracticeFollowUpService}）。
     *
     * 题目列表与题目数量必须一起改：前端按 questionCount 显示进度，
     * 报告页按 questionsJson 取题干 —— 只改一个就会出现"第 6/5 题"这种自相矛盾的状态。
     *
     * 位置是**插在被打断的那道题后面**，不是追加到末尾：前端也是这么插的
     * （`draft.questions.splice(currentIndex + 1, 0, ...)`），两边一旦不一致，
     * 下一道题的 questionIndex 就对不上，保存回答会直接 400。
     */
    @Transactional
    public PracticeQuestion appendFollowUpQuestion(UUID sessionId, int afterIndex, String parentQuestionId,
                                                   String text, String category) {
        PracticeSessionEntity session = sessions.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Practice session not found"));
        List<PracticeQuestion> questions = new ArrayList<>(readQuestions(session.getQuestionsJson()));
        // 位置校验：只有"列表里的第 afterIndex 道题确实是父题"时才插在它后面，
        // 否则退回追加到末尾 —— 宁可在题序上保守一点，也不能让用户答到一半报错。
        int position = afterIndex >= 0 && afterIndex < questions.size()
                && questions.get(afterIndex).id().equals(parentQuestionId)
                ? afterIndex + 1 : questions.size();
        PracticeQuestion question = new PracticeQuestion(nextFollowUpId(questions, afterIndex), text, category);
        questions.add(position, question);
        session.setQuestionsJson(writeQuestions(questions));
        session.setQuestionCount(questions.size());
        sessions.save(session);
        return question;
    }

    /** 追问 id 要唯一且看得出挂在谁的下面（`q-3-follow-2` = 第 3 题的第二次追问）。 */
    private static String nextFollowUpId(List<PracticeQuestion> questions, int parentIndex) {
        for (int round = 1; ; round++) {
            String candidate = "q-%d-follow-%d".formatted(Math.max(parentIndex, 0), round);
            boolean taken = questions.stream().anyMatch(question -> candidate.equals(question.id()));
            if (!taken) return candidate;
        }
    }

    /** 当场追问要现转写：结果落回回答上，随后的批量分析就不用再转一次。 */
    @Transactional
    public void saveTranscript(UUID answerId, String transcript) {
        answers.findById(answerId).ifPresent(answer -> {
            answer.setTranscriptText(transcript);
            answer.setTranscriptStatus("completed");
            answers.save(answer);
        });
    }

    private PracticeSessionView toView(PracticeSessionEntity session, List<PracticeAnswerView> sessionAnswers) {
        return new PracticeSessionView(session.getId(), session.getGoalId(), session.getRole(), session.getCompany(),
                session.getJobDescription(), session.getResumeName(), session.getInterviewType(),
                session.getDifficulty(), session.getInterviewerStyle(), session.getQuestionCount(),
                session.getDurationMinutes(), session.isFollowUpEnabled(),
                session.getMode(), session.getPausedCount(),
                readQuestions(session.getQuestionsJson()), session.getStatus(),
                session.getCreatedAt(), session.getStartedAt(), session.getCompletedAt(),
                session.getRetentionUntil(), sessionAnswers);
    }

    private List<PracticeAnswerView> answerViews(UUID sessionId) {
        return answers.findAllBySessionIdOrderByQuestionIndexAsc(sessionId).stream()
                .map(this::toAnswerView)
                .toList();
    }

    private PracticeAnswerView toAnswerView(PracticeAnswerEntity answer) {
        return new PracticeAnswerView(answer.getId(), answer.getQuestionIndex(), answer.getQuestionId(),
                answer.getMediaContentType(), answer.getMediaSizeBytes(), answer.getDurationSeconds(),
                answer.getStartedAtOffsetSeconds(), answer.getTranscriptStatus(), answer.getTranscriptText(),
                answer.getTranscriptOriginal(), answer.getTranscriptEditedAt(),
                answer.getEvaluationStatus(), answer.getCreatedAt());
    }

    private static String normalizeMode(String mode) {
        return "continuous".equalsIgnoreCase(mode) ? "continuous" : "per_question";
    }

    private String writeQuestions(List<PracticeQuestion> questions) {
        try {
            return objectMapper.writeValueAsString(questions);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize interview questions", exception);
        }
    }

    private List<PracticeQuestion> readQuestions(String questionsJson) {
        try {
            return objectMapper.readValue(questionsJson, QUESTIONS_TYPE);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read interview questions", exception);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record MediaFile(Resource resource, String contentType) { }
}
