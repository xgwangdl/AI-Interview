package com.guangge.Interview.jobradar;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Applies V1-V21 to a real PostgreSQL database and verifies the constraints that a
 * H2/unit test cannot prove. Skipped unless -Ddb.integration=true is passed.
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
class JobRadarPersistenceIntegrationTest {
    @Autowired private JobLeadRepository leads;
    @Autowired private JobEvaluationRepository evaluations;
    @Autowired private JobAgentRunRepository runs;
    @Autowired private InterviewDebriefRepository debriefs;
    @Autowired private TrainingWeakPointRepository weakPoints;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void flywayAppliesV21OnTopOfTheExistingSchema() {
        String version = jdbc.queryForObject("""
                select version from flyway_schema_history
                 where success = true and version is not null
                 order by installed_rank desc limit 1
                """, String.class);

        assertThat(version).isEqualTo("21");
        assertThat(tableExists("job_agent_runs")).isTrue();
        assertThat(tableExists("agent_runs")).isFalse();
        assertThat(columnExists("candidates", "skill_profile_json")).isTrue();
    }

    @Test
    void jsonbFieldsRoundTripThroughJpa() {
        JobLeadEntity lead = lead("json-owner", "json-dedup");
        leads.saveAndFlush(lead);

        JobEvaluationEntity evaluation = evaluation(lead);
        evaluations.saveAndFlush(evaluation);
        JobAgentRunEntity run = run("json-owner", lead.getId(), "json-idempotency");
        runs.saveAndFlush(run);
        entityManager.clear();

        JobEvaluationEntity loadedEvaluation = evaluations.findById(evaluation.getId()).orElseThrow();
        assertThat(loadedEvaluation.getBreakdownJson().path("skill").asInt()).isEqualTo(88);
        assertThat(loadedEvaluation.getGapsJson().get(0).path("name").asText()).isEqualTo("Kafka");

        JobAgentRunEntity loadedRun = runs.findById(run.getId()).orElseThrow();
        assertThat(loadedRun.getInputJson().path("source").asText()).isEqualTo("manual_paste");
        assertThat(loadedRun.getStepsJson().get(0).path("step").asText()).isEqualTo("extract");
    }

    @Test
    void dedupKeyIsOwnerScopedAndUniquePerOwner() {
        JobLeadEntity alice = lead("alice", "same-key");
        JobLeadEntity bob = lead("bob", "same-key");

        leads.saveAndFlush(alice);
        leads.saveAndFlush(bob);

        assertThat(leads.findByOwnerIdAndDedupKey("alice", "same-key"))
                .map(JobLeadEntity::getId).contains(alice.getId());
        assertThat(leads.findByOwnerIdAndDedupKey("bob", "same-key"))
                .map(JobLeadEntity::getId).contains(bob.getId());
        assertThat(leads.findByIdAndOwnerId(alice.getId(), "bob")).isEmpty();
        assertThatThrownBy(() -> leads.saveAndFlush(lead("alice", "same-key")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void searchAcceptsNullOptionalFiltersOnPostgres() {
        JobLeadEntity lead = lead("search-owner", "search-dedup");
        leads.saveAndFlush(lead);

        var page = leads.search("search-owner", null, null, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(JobLeadEntity::getId).containsExactly(lead.getId());
    }

    @Test
    void deletingADebriefPhysicallyCascadesItsWeakPoints() {
        InterviewDebriefEntity debrief = debrief("alice");
        debriefs.saveAndFlush(debrief);
        TrainingWeakPointEntity weakPoint = weakPoint("alice", debrief.getId());
        weakPoints.saveAndFlush(weakPoint);

        debriefs.delete(debrief);
        debriefs.flush();
        entityManager.clear();

        assertThat(debriefs.findById(debrief.getId())).isEmpty();
        assertThat(weakPoints.findById(weakPoint.getId())).isEmpty();
        assertThat(weakPoints.findAllByDebriefId(debrief.getId())).isEmpty();
    }

    @Test
    void deletingALeadCascadesItsEvaluation() {
        JobLeadEntity lead = lead("cascade-owner", "cascade-dedup");
        leads.saveAndFlush(lead);
        JobEvaluationEntity evaluation = evaluation(lead);
        evaluations.saveAndFlush(evaluation);

        leads.delete(lead);
        leads.flush();
        entityManager.clear();

        assertThat(leads.findById(lead.getId())).isEmpty();
        assertThat(evaluations.findById(evaluation.getId())).isEmpty();
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject("""
                select count(*) from information_schema.tables
                 where table_schema = current_schema() and table_name = ?
                """, Integer.class, table);
        return count != null && count > 0;
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                 where table_schema = current_schema() and table_name = ? and column_name = ?
                """, Integer.class, table, column);
        return count != null && count > 0;
    }

    private JobLeadEntity lead(String owner, String dedupKey) {
        JobLeadEntity lead = new JobLeadEntity();
        lead.setId(UUID.randomUUID());
        lead.setOwnerId(owner);
        lead.setCompany("Acme");
        lead.setRole("Java Engineer");
        lead.setJdText("完整岗位描述".repeat(30));
        lead.setJdHash(JobRadarHashing.sha256(lead.getJdText()));
        lead.setDedupKey(dedupKey);
        lead.setSource("manual_paste");
        lead.setStatus(JobLeadStatusMachine.SHORTLISTED);
        lead.setCreatedAt(Instant.now());
        lead.setUpdatedAt(Instant.now());
        return lead;
    }

    private JobEvaluationEntity evaluation(JobLeadEntity lead) {
        JobEvaluationEntity evaluation = new JobEvaluationEntity();
        evaluation.setId(UUID.randomUUID());
        evaluation.setOwnerId(lead.getOwnerId());
        evaluation.setJobLeadId(lead.getId());
        evaluation.setJdHash(lead.getJdHash());
        evaluation.setProfileHash("profile-hash");
        evaluation.setPromptVersion("v1");
        evaluation.setScore(82);
        evaluation.setConfidence("HIGH");
        evaluation.setBreakdownJson(objectMapper.createObjectNode().put("skill", 88));
        evaluation.setHitsJson(objectMapper.createArrayNode().add(objectMapper.createObjectNode().put("name", "Java")));
        evaluation.setGapsJson(objectMapper.createArrayNode().add(objectMapper.createObjectNode().put("name", "Kafka")));
        evaluation.setRecommendation("apply");
        evaluation.setDegraded(false);
        evaluation.setCreatedAt(Instant.now());
        return evaluation;
    }

    private JobAgentRunEntity run(String owner, UUID leadId, String idempotencyKey) {
        Instant now = Instant.now();
        JobAgentRunEntity run = new JobAgentRunEntity();
        run.setId(UUID.randomUUID());
        run.setOwnerId(owner);
        run.setKind(JobAgentRunService.KIND_JOB_EVALUATE);
        run.setStatus(JobAgentRunService.STATUS_PENDING);
        run.setIdempotencyKey(idempotencyKey);
        run.setSubjectType("job_lead");
        run.setSubjectId(leadId);
        run.setInputJson(objectMapper.createObjectNode().put("source", "manual_paste"));
        run.setStepsJson(objectMapper.createArrayNode().add(objectMapper.createObjectNode().put("step", "extract")));
        run.setAttempts(0);
        run.setModelCalls(0);
        run.setMinutesSaved(0);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        return run;
    }

    private InterviewDebriefEntity debrief(String owner) {
        Instant now = Instant.now();
        InterviewDebriefEntity debrief = new InterviewDebriefEntity();
        debrief.setId(UUID.randomUUID());
        debrief.setOwnerId(owner);
        debrief.setInterviewDate(LocalDate.now());
        debrief.setRoundName("一面");
        debrief.setOutcome("unknown");
        debrief.setRawText("Redis 缓存雪崩与项目追问的完整回填内容");
        debrief.setRawTextSha256(JobRadarHashing.sha256(debrief.getRawText()));
        debrief.setRawTextLength(debrief.getRawText().length());
        debrief.setPrivacyAcknowledgedAt(now);
        debrief.setParseStatus("parsed");
        debrief.setCreatedAt(now);
        debrief.setUpdatedAt(now);
        return debrief;
    }

    private TrainingWeakPointEntity weakPoint(String owner, UUID debriefId) {
        TrainingWeakPointEntity weakPoint = new TrainingWeakPointEntity();
        weakPoint.setId(UUID.randomUUID());
        weakPoint.setOwnerId(owner);
        weakPoint.setSource("real_interview");
        weakPoint.setDebriefId(debriefId);
        weakPoint.setTopic("Redis 缓存雪崩");
        weakPoint.setDimensionKey("professionalism");
        weakPoint.setEvidence("无法说明降级与限流策略");
        weakPoint.setWeight(5);
        weakPoint.setStatus("open");
        weakPoint.setCreatedAt(Instant.now());
        return weakPoint;
    }
}
