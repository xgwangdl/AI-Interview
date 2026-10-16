package com.guangge.Interview.practice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.analysis.AnalysisJobEntity;
import com.guangge.Interview.analysis.AnalysisJobRepository;
import com.guangge.Interview.practice.entity.PracticeAnswerEntity;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.repository.PracticeAnswerRepository;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import com.guangge.Interview.practice.service.PracticeSessionService;
import com.guangge.Interview.practice.storage.LocalMediaStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Applies the real Flyway migrations to a real PostgreSQL + pgvector database and exercises the
 * practice schema through JPA. Skipped unless -Ddb.integration=true is passed.
 *
 * docker compose up -d postgres, then mvn -B test -Ddb.integration=true
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/interview",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfSystemProperty(named = "db.integration", matches = "true")
class PracticePersistenceIntegrationTest {

    @TempDir
    Path mediaDir;

    @Autowired
    private PracticeSessionRepository sessions;

    @Autowired
    private PracticeAnswerRepository answers;

    @Autowired
    private AnalysisJobRepository jobs;

    @Test
    void flywaySchemaPersistsAndReadsBackAPracticeSession() {
        PracticeSessionEntity session = newSession();

        sessions.saveAndFlush(session);

        PracticeSessionEntity loaded = sessions.findByIdAndOwnerId(session.getId(), "alice").orElseThrow();
        assertEquals("Java 后端", loaded.getRole());
        assertEquals(5, loaded.getQuestionCount());
        assertTrue(loaded.getRetentionUntil().isAfter(loaded.getCreatedAt()));
    }

    @Test
    void answerTableKeepsOneRowPerSessionQuestion() {
        PracticeSessionEntity session = newSession();
        sessions.saveAndFlush(session);
        answers.saveAndFlush(newAnswer(session.getId(), 0));

        DataIntegrityViolationException conflict = assertThrows(DataIntegrityViolationException.class,
                () -> answers.saveAndFlush(newAnswer(session.getId(), 0)));

        String message = conflict.getMessage() + " " + conflict.getMostSpecificCause().getMessage();
        assertTrue(message.contains("uq_practice_answers_session_question"), message);
    }

    @Test
    void analysisJobsReferenceAStoredAnswer() {
        PracticeSessionEntity session = newSession();
        sessions.saveAndFlush(session);
        PracticeAnswerEntity answer = answers.saveAndFlush(newAnswer(session.getId(), 1));

        Instant now = Instant.now();
        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setId(UUID.randomUUID());
        job.setSessionId(session.getId());
        job.setAnswerId(answer.getId());
        job.setStatus("pending");
        job.setAttempts(0);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        jobs.saveAndFlush(job);

        assertEquals(1, jobs.findAllByStatusOrderByCreatedAtAsc("pending").size());
        assertEquals(answer.getId(), jobs.findByAnswerId(answer.getId()).orElseThrow().getAnswerId());
    }

    /**
     * 「录像只保留 7 天」这条承诺背后就是它：过期会话必须连同录像文件、回答与分析任务一起消失。
     * analysis_jobs 的清理依赖外键级联，只有真库才能验证。
     */
    @Test
    void expiredSessionsAreDeletedTogetherWithMediaAndJobs() throws IOException {
        LocalMediaStorage storage = new LocalMediaStorage(mediaDir.toString());
        PracticeSessionService service =
                new PracticeSessionService(sessions, answers, storage, new ObjectMapper(),
                        mock(com.guangge.Interview.practice.archive.PracticeScoreArchiveService.class), 7);

        PracticeSessionEntity expired = newSession();
        expired.setRetentionUntil(Instant.now().minusSeconds(3600));
        sessions.saveAndFlush(expired);
        PracticeAnswerEntity answer = answers.saveAndFlush(newAnswer(expired.getId(), 0));
        byte[] recording = "expired recording".getBytes(StandardCharsets.UTF_8);
        storage.store(answer.getMediaKey(), new ByteArrayInputStream(recording), recording.length);

        Instant now = Instant.now();
        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setId(UUID.randomUUID());
        job.setSessionId(expired.getId());
        job.setAnswerId(answer.getId());
        job.setStatus("pending");
        job.setAttempts(0);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        jobs.saveAndFlush(job);

        assertTrue(storage.find(answer.getMediaKey()).isPresent());
        assertTrue(jobs.findByAnswerId(answer.getId()).isPresent());

        service.deleteExpiredSessions();

        assertTrue(sessions.findById(expired.getId()).isEmpty());
        assertTrue(answers.findAllBySessionIdOrderByQuestionIndexAsc(expired.getId()).isEmpty());
        assertTrue(jobs.findByAnswerId(answer.getId()).isEmpty());
        assertTrue(storage.find(answer.getMediaKey()).isEmpty());
    }

    private static PracticeSessionEntity newSession() {
        Instant now = Instant.now();
        PracticeSessionEntity session = new PracticeSessionEntity();
        session.setId(UUID.randomUUID());
        session.setOwnerId("alice");
        session.setRole("Java 后端");
        session.setInterviewType("technical");
        session.setDifficulty("standard");
        session.setInterviewerStyle("balanced");
        session.setQuestionCount(5);
        session.setDurationMinutes(20);
        session.setQuestionsJson("[{\"id\":\"q-0\",\"text\":\"first\",\"category\":\"design\"}]");
        session.setStatus("completed");
        session.setCreatedAt(now);
        session.setRetentionUntil(now.plusSeconds(7 * 86400L));
        return session;
    }

    private static PracticeAnswerEntity newAnswer(UUID sessionId, int questionIndex) {
        PracticeAnswerEntity answer = new PracticeAnswerEntity();
        answer.setId(UUID.randomUUID());
        answer.setSessionId(sessionId);
        answer.setQuestionIndex(questionIndex);
        answer.setQuestionId("q-" + questionIndex);
        answer.setMediaKey("media-" + questionIndex);
        answer.setMediaContentType("video/webm");
        answer.setMediaSizeBytes(1024L);
        answer.setDurationSeconds(60);
        answer.setTranscriptStatus("awaiting_provider");
        answer.setEvaluationStatus("awaiting_provider");
        answer.setCreatedAt(Instant.now());
        return answer;
    }
}
