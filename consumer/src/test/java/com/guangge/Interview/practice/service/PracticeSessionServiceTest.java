package com.guangge.Interview.practice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.practice.dto.PracticeAnswerRequest;
import com.guangge.Interview.practice.dto.PracticeQuestion;
import com.guangge.Interview.practice.dto.PracticeSessionRequest;
import com.guangge.Interview.practice.entity.PracticeAnswerEntity;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.repository.PracticeAnswerRepository;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import com.guangge.Interview.practice.storage.MediaStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
        service = new PracticeSessionService(sessions, answers, mock(MediaStorage.class), new ObjectMapper(), 7);
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
        PracticeSessionRequest request = new PracticeSessionRequest("Java 后端", null, null, null,
                "technical", "standard", "balanced", 1, 4,
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
        when(answers.findAllBySessionIdOrderByQuestionIndexAsc(sessionId)).thenReturn(List.of(answer));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.complete("alice", sessionId));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(sessions, never()).save(any(PracticeSessionEntity.class));
    }

    @Test
    void completesSessionWhenRecordedAnswerMediaIsUploaded() {
        UUID sessionId = UUID.randomUUID();
        PracticeSessionEntity session = ownedSession(sessionId);
        PracticeAnswerEntity answer = answer(sessionId, answerId());
        when(sessions.findByIdAndOwnerId(sessionId, "alice")).thenReturn(Optional.of(session));
        when(answers.findAllBySessionIdOrderByQuestionIndexAsc(sessionId)).thenReturn(List.of(answer));
        when(sessions.save(any(PracticeSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var completed = service.complete("alice", sessionId);

        assertEquals("completed", completed.status());
        assertEquals(1, completed.answers().size());
        verify(sessions).save(session);
    }

    @Test
    void rejectsNonVideoMediaUploads() {
        UUID sessionId = UUID.randomUUID();
        UUID answerId = answerId();
        PracticeAnswerEntity answer = answer(sessionId, answerId);
        when(answers.findById(answerId)).thenReturn(Optional.of(answer));
        when(sessions.findByIdAndOwnerId(sessionId, "alice")).thenReturn(Optional.of(ownedSession(sessionId)));
        MediaStorage storage = mock(MediaStorage.class);
        service = new PracticeSessionService(sessions, answers, storage, new ObjectMapper(), 7);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.storeAnswerMedia("alice", answerId, "text/plain", 12,
                        new java.io.ByteArrayInputStream(new byte[12])));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, error.getStatusCode());
        verify(storage, never()).store(any(), any());
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

    private static String answerId() {
        return UUID.randomUUID().toString();
    }
}
