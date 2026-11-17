package com.guangge.Interview.privacy;

import com.guangge.Interview.practice.storage.MediaStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Runs the account export/delete SQL against the real Flyway schema. Skipped unless
 * {@code -Ddb.integration=true} is passed.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/${test.database:interview}",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfSystemProperty(named = "db.integration", matches = "true")
class AccountDataPersistenceIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private MediaStorage media;
    private AccountDataService service;

    @BeforeEach
    void setUp() {
        media = mock(MediaStorage.class);
        service = new AccountDataService(jdbc, media);
    }

    @Test
    void exportsAndPhysicallyDeletesEverySupportedAccountDomain() throws Exception {
        String owner = "privacy-integration-owner";
        jdbc.update("delete from candidates where candidate_name = ?", owner);
        jdbc.update("""
                insert into candidates (candidate_name, invitation_code, email)
                values (?, 'hash', 'privacy@example.com')
                """, owner);
        jdbc.update("""
                insert into privacy_consents (id, owner_id, consent_type, version, accepted_at)
                values (gen_random_uuid(), ?, 'recording', 'v1', now())
                """, owner);
        jdbc.update("""
                insert into practice_sessions (id, owner_id, role, interview_type, difficulty,
                    interviewer_style, question_count, duration_minutes, questions_json, status,
                    created_at, retention_until, follow_up_enabled, mode, paused_count)
                values ('11111111-1111-1111-1111-111111111111', ?, 'Java', 'technical', 'standard',
                    'balanced', 1, 20, '[]', 'completed', now(), now() + interval '7 days', false,
                    'per_question', 0)
                """, owner);
        jdbc.update("""
                insert into practice_answers (id, session_id, question_index, question_id, attempt,
                    is_current, media_key, media_content_type, media_size_bytes, transcript_status,
                    evaluation_status, created_at)
                values ('22222222-2222-2222-2222-222222222222',
                    '11111111-1111-1111-1111-111111111111', 0, 'q-0', 1, true, 'media-1', 'video/webm',
                    100, 'completed', 'completed', now())
                """);
        jdbc.update("""
                insert into resumes (id, owner_id, title, source, raw_text, version, created_at, updated_at)
                values (gen_random_uuid(), ?, 'Resume', 'manual', 'Java', 1, now(), now())
                """, owner);
        jdbc.update("""
                insert into language_sessions (id, owner_id, language, scenario, level, status, turn_count,
                    max_turns, created_at, retention_until)
                values ('33333333-3333-3333-3333-333333333333', ?, 'en', 'interview', 'intermediate',
                    'finished', 0, 8, now(), now() + interval '7 days')
                """, owner);

        Map<String, Object> exported = service.export(owner);
        assertThat((List<?>) exported.get("privacyConsents")).hasSize(1);
        assertThat((List<?>) exported.get("practiceSessions")).hasSize(1);
        assertThat((List<?>) exported.get("practiceAnswers")).hasSize(1);
        assertThat((List<?>) exported.get("resumes")).hasSize(1);
        assertThat((List<?>) exported.get("languageSessions")).hasSize(1);

        service.deleteAccount(owner, owner);

        verify(media).delete("media-1");
        assertThat(count("candidates", "candidate_name", owner)).isZero();
        assertThat(count("privacy_consents", "owner_id", owner)).isZero();
        assertThat(count("practice_sessions", "owner_id", owner)).isZero();
        assertThat(count("resumes", "owner_id", owner)).isZero();
        assertThat(count("language_sessions", "owner_id", owner)).isZero();
    }

    private int count(String table, String column, String owner) {
        Integer value = jdbc.queryForObject(
                "select count(*) from " + table + " where " + column + " = ?", Integer.class, owner);
        return value == null ? 0 : value;
    }
}
