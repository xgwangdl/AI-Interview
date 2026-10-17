package com.guangge.Interview.practice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.practice.dto.PracticeAnswerRequest;
import com.guangge.Interview.practice.dto.PracticeAnswerView;
import com.guangge.Interview.practice.dto.PracticeQuestion;
import com.guangge.Interview.practice.dto.PracticeSessionRequest;
import com.guangge.Interview.practice.entity.PracticeAnswerEntity;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.repository.PracticeAnswerRepository;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import com.guangge.Interview.practice.storage.MediaStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeSessionServiceTest {
    private PracticeSessionRepository sessions;
    private PracticeAnswerRepository answers;
    private PracticeSessionService service;

    @BeforeEach
    void setUp() {
        sessions = mock(PracticeSessionRepository.class);
        answers = mock(PracticeAnswerRepository.class);
        service = new PracticeSessionService(sessions, answers, mock(MediaStorage.class), new ObjectMapper(),
                mock(com.guangge.Interview.practice.archive.PracticeScoreArchiveService.class), 7);
    }

    @Test
    void insertsFollowUpRightAfterTheQuestionItDigsInto() {
        UUID sessionId = UUID.randomUUID();
        PracticeSessionEntity session = new PracticeSessionEntity();
        session.setId(sessionId);
        session.setQuestionsJson("""
                [{"id":"q-1","text":"第一题","category":"design"},
                 {"id":"q-2","text":"第二题","category":"design"}]
                """);
        session.setQuestionCount(2);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));

        PracticeQuestion created = service.appendFollowUpQuestion(sessionId, 0, "q-1", "你刚才说的重构改了哪里？", "follow_up");

        // 插在末尾会让前端的题号与服务端错位（保存回答直接 400），所以必须插在原题后面
        assertEquals("[q-1, q-0-follow-1, q-2]", idsInOrder(session));
        assertEquals(3, session.getQuestionCount());
        assertEquals("q-0-follow-1", created.id());
    }

    @Test
    void fallsBackToAppendingWhenTheParentIsNotWhereWeThinkItIs() {
        UUID sessionId = UUID.randomUUID();
        PracticeSessionEntity session = new PracticeSessionEntity();
        session.setId(sessionId);
        session.setQuestionsJson("[{\"id\":\"q-1\",\"text\":\"第一题\",\"category\":\"design\"}]");
        session.setQuestionCount(1);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));

        // 题号对不上（旧数据、客户端与服务端不同步）：宁可放到末尾，也不能让追问把练习打断
        service.appendFollowUpQuestion(sessionId, 0, "q-999", "换个问法", "follow_up");

        assertEquals("[q-1, q-0-follow-1]", idsInOrder(session));
    }

    private static String idsInOrder(PracticeSessionEntity session) {
        try {
            List<PracticeQuestion> questions = new ObjectMapper().readValue(
                    session.getQuestionsJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<PracticeQuestion>>() { });
            return questions.stream().map(PracticeQuestion::id).toList().toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void storesContinuousModeAndAllowsOnePause() {
        PracticeSessionRequest request = new PracticeSessionRequest("Java 后端", null, null, null, null,
                "technical", "standard", "balanced", 1, 20,
                true, "continuous",
                List.of(new PracticeQuestion("q-1", "Explain a design", "design")));
        when(sessions.save(any(PracticeSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create("alice", request);
        ArgumentCaptor<PracticeSessionEntity> captor = ArgumentCaptor.forClass(PracticeSessionEntity.class);
        verify(sessions).save(captor.capture());
        when(sessions.findByIdAndOwnerId(created.id(), "alice")).thenReturn(Optional.of(captor.getValue()));

        assertEquals("continuous", created.mode());
        assertEquals(0, created.pausedCount());
        var paused = service.pause("alice", created.id());
        assertEquals(1, paused.pausedCount());
        ResponseStatusException second = assertThrows(ResponseStatusException.class,
                () -> service.pause("alice", created.id()));
        assertEquals(HttpStatus.CONFLICT, second.getStatusCode());
    }

    @Test
    void hidesSessionsOwnedByAnotherUser() {
        UUID sessionId = UUID.randomUUID();
        when(sessions.findByIdAndOwnerId(sessionId, "alice")).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.get("alice", sessionId));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(answers, never()).findAllBySessionIdOrderByQuestionIndexAsc(any());
    }

    @Test
    void rejectsQuestionIdThatDoesNotMatchRequestedQuestionIndex() throws Exception {
        UUID sessionId = UUID.randomUUID();
        PracticeSessionEntity session = new PracticeSessionEntity();
        session.setId(sessionId);
        session.setOwnerId("alice");
        session.setQuestionCount(2);
        session.setStatus("in_progress");
        session.setQuestionsJson(new ObjectMapper().writeValueAsString(List.of(
                new PracticeQuestion("q-1", "First question", "intro"),
                new PracticeQuestion("q-2", "Second question", "design"))));
        when(sessions.findByIdAndOwnerId(sessionId, "alice")).thenReturn(Optional.of(session));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.saveAnswer("alice", sessionId, new PracticeAnswerRequest(0, "q-2", 30, 0)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(answers, never()).save(any());
    }

    @Test
    void createsSessionWithAuthenticatedOwnerAndRetentionDeadline() {
        PracticeSessionRequest request = new PracticeSessionRequest("Java 后端", null, null, null, null,
                "technical", "standard", "balanced", 1, 4,
                false, "per_question",
                List.of(new PracticeQuestion("q-1", "Explain a design", "design")));
        when(sessions.save(any(PracticeSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create("alice", request);

        assertEquals("Java 后端", created.role());
        assertEquals(1, created.questions().size());
        assertEquals("in_progress", created.status());
        assertEquals(created.createdAt().plusSeconds(7 * 86400L), created.retentionUntil());
        verify(sessions).save(any(PracticeSessionEntity.class));
    }

    @Test
    void refusesToCompleteUntilAnswerMediaHasUploaded() {
        UUID sessionId = UUID.randomUUID();
        PracticeSessionEntity session = ownedSession(sessionId);
        PracticeAnswerEntity answer = answer(sessionId, null);
        when(sessions.findByIdAndOwnerId(sessionId, "alice")).thenReturn(Optional.of(session));
        when(answers.findAllBySessionIdAndIsCurrentTrueOrderByQuestionIndexAsc(sessionId)).thenReturn(List.of(answer));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.complete("alice", sessionId));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(sessions, never()).save(any(PracticeSessionEntity.class));
    }

    @Test
    void completesSessionWhenRecordedAnswerMediaIsUploaded() {
        UUID sessionId = UUID.randomUUID();
        PracticeSessionEntity session = ownedSession(sessionId);
        PracticeAnswerEntity answer = answer(sessionId, mediaKey());
        when(sessions.findByIdAndOwnerId(sessionId, "alice")).thenReturn(Optional.of(session));
        when(answers.findAllBySessionIdAndIsCurrentTrueOrderByQuestionIndexAsc(sessionId)).thenReturn(List.of(answer));
        when(sessions.save(any(PracticeSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var completed = service.complete("alice", sessionId);

        assertEquals("completed", completed.status());
        assertEquals(1, completed.answers().size());
        verify(sessions).save(session);
    }

    @Test
    void createsAnInactiveNextAttempt() {
        UUID sessionId = UUID.randomUUID();
        PracticeSessionEntity session = ownedSession(sessionId);
        session.setQuestionsJson("[{\"id\":\"q-1\",\"text\":\"讲一个项目\",\"category\":\"项目\"}]");
        session.setQuestionCount(1);
        PracticeAnswerEntity first = answer(sessionId, mediaKey());
        first.setAttempt(1);
        first.setCurrent(true);
        first.setRewriteText("改写版本");
        when(sessions.findByIdAndOwnerId(sessionId, "alice")).thenReturn(Optional.of(session));
        when(answers.findAllBySessionIdAndQuestionIndexOrderByAttemptAsc(sessionId, 0))
                .thenReturn(List.of(first));
        when(answers.save(any(PracticeAnswerEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.createAttempt("alice", sessionId, 0);

        assertEquals(2, created.attempt());
        assertEquals(false, created.isCurrent());
        assertEquals("改写版本", created.rewriteText());
        assertEquals("in_progress", session.getStatus());
    }

    @Test
    void discardsOnlyAnInactiveAttempt() throws Exception {
        UUID sessionId = UUID.randomUUID();
        PracticeSessionEntity session = ownedSession(sessionId);
        PracticeAnswerEntity attempt = answer(sessionId, null);
        attempt.setAttempt(2);
        attempt.setCurrent(false);
        when(answers.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(sessions.findByIdAndOwnerId(sessionId, "alice")).thenReturn(Optional.of(session));

        service.discardAttempt("alice", attempt.getId());
        verify(answers).delete(attempt);
    }

    @Test
    void rejectsNonVideoMediaUploads() throws Exception {
        UUID sessionId = UUID.randomUUID();
        PracticeAnswerEntity answer = answer(sessionId, mediaKey());
        UUID answerId = answer.getId();
        when(answers.findById(answerId)).thenReturn(Optional.of(answer));
        when(sessions.findByIdAndOwnerId(sessionId, "alice")).thenReturn(Optional.of(ownedSession(sessionId)));
        MediaStorage storage = mock(MediaStorage.class);
        service = new PracticeSessionService(sessions, answers, storage, new ObjectMapper(),
                mock(com.guangge.Interview.practice.archive.PracticeScoreArchiveService.class), 7);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.storeAnswerMedia("alice", answerId, "text/plain", 12,
                        new java.io.ByteArrayInputStream(new byte[12])));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, error.getStatusCode());
        verify(storage, never()).store(any(), any(), anyLong());
    }

    @Test
    void completeAnswerRequiresUploadedMedia() {
        UUID sessionId = UUID.randomUUID();
        PracticeAnswerEntity answer = answer(sessionId, null);
        when(answers.findById(answer.getId())).thenReturn(Optional.of(answer));
        when(sessions.findByIdAndOwnerId(sessionId, "alice")).thenReturn(Optional.of(ownedSession(sessionId)));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.completeAnswer("alice", answer.getId()));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void completeAnswerReturnsTheUploadedAnswer() {
        UUID sessionId = UUID.randomUUID();
        PracticeAnswerEntity answer = answer(sessionId, mediaKey());
        when(answers.findById(answer.getId())).thenReturn(Optional.of(answer));
        when(sessions.findByIdAndOwnerId(sessionId, "alice")).thenReturn(Optional.of(ownedSession(sessionId)));

        PracticeAnswerView view = service.completeAnswer("alice", answer.getId());

        assertEquals(answer.getId(), view.id());
    }

    private static PracticeSessionEntity ownedSession(UUID id) {
        PracticeSessionEntity session = new PracticeSessionEntity();
        session.setId(id);
        session.setOwnerId("alice");
        session.setRole("Java backend");
        session.setInterviewType("technical");
        session.setDifficulty("standard");
        session.setInterviewerStyle("balanced");
        session.setQuestionCount(1);
        session.setDurationMinutes(4);
        session.setQuestionsJson("[{\"id\":\"q-1\",\"text\":\"Question\",\"category\":\"design\"}]");
        session.setStatus("in_progress");
        return session;
    }

    private static PracticeAnswerEntity answer(UUID sessionId, String mediaKey) {
        PracticeAnswerEntity answer = new PracticeAnswerEntity();
        answer.setId(UUID.randomUUID());
        answer.setSessionId(sessionId);
        answer.setQuestionIndex(0);
        answer.setQuestionId("q-1");
        answer.setMediaKey(mediaKey);
        answer.setMediaContentType(mediaKey == null ? null : "video/webm");
        answer.setMediaSizeBytes(mediaKey == null ? null : 12L);
        answer.setTranscriptStatus("awaiting_provider");
        answer.setEvaluationStatus("awaiting_provider");
        return answer;
    }

    private static String mediaKey() {
        return UUID.randomUUID().toString();
    }
}
