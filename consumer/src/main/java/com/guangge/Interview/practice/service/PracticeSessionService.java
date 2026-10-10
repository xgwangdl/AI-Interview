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
import java.util.List;
import java.util.UUID;

@Service
public class PracticeSessionService {
    private static final TypeReference<List<PracticeQuestion>> QUESTIONS_TYPE = new TypeReference<>() { };

    private final PracticeSessionRepository sessions;
    private final PracticeAnswerRepository answers;
    private final MediaStorage mediaStorage;
    private final ObjectMapper objectMapper;
    private final int retentionDays;

    public PracticeSessionService(
            PracticeSessionRepository sessions,
            PracticeAnswerRepository answers,
            MediaStorage mediaStorage,
            ObjectMapper objectMapper,
            @Value("${practice.media.retention-days:7}") int retentionDays) {
        this.sessions = sessions;
        this.answers = answers;
        this.mediaStorage = mediaStorage;
        this.objectMapper = objectMapper;
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
        session.setRole(request.role().trim());
        session.setCompany(trimToNull(request.company()));
        session.setJobDescription(trimToNull(request.jobDescription()));
        session.setResumeName(trimToNull(request.resumeName()));
        session.setInterviewType(request.interviewType());
        session.setDifficulty(request.difficulty());
        session.setInterviewerStyle(request.interviewerStyle());
        session.setQuestionCount(request.questionCount());
        session.setDurationMinutes(request.durationMinutes());
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
        deleteSession(session);
    }

    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    public void deleteExpiredSessions() {
        for (PracticeSessionEntity session : sessions.findAllByRetentionUntilBefore(Instant.now())) {
            try {
                deleteSession(session);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to remove expired practice media", exception);
            }
        }
    }

    private void deleteSession(PracticeSessionEntity session) throws IOException {
        List<PracticeAnswerEntity> sessionAnswers = answers.findAllBySessionIdOrderByQuestionIndexAsc(session.getId());
        for (PracticeAnswerEntity answer : sessionAnswers) {
            if (answer.getMediaKey() != null) mediaStorage.delete(answer.getMediaKey());
        }
        answers.deleteAllBySessionId(session.getId());
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

    private PracticeSessionView toView(PracticeSessionEntity session, List<PracticeAnswerView> sessionAnswers) {
        return new PracticeSessionView(session.getId(), session.getRole(), session.getCompany(),
                session.getJobDescription(), session.getResumeName(), session.getInterviewType(),
                session.getDifficulty(), session.getInterviewerStyle(), session.getQuestionCount(),
                session.getDurationMinutes(), readQuestions(session.getQuestionsJson()), session.getStatus(),
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
                answer.getEvaluationStatus(), answer.getCreatedAt());
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
