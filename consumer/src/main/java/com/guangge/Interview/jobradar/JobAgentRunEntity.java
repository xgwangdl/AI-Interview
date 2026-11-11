package com.guangge.Interview.jobradar;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_agent_runs")
public class JobAgentRunEntity {
    @Id
    private UUID id;
    @Column(name = "owner_id", nullable = false, length = 80)
    private String ownerId;
    @Column(nullable = false, length = 40)
    private String kind;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "idempotency_key", nullable = false, length = 160)
    private String idempotencyKey;
    @Column(name = "subject_type", length = 30)
    private String subjectType;
    @Column(name = "subject_id")
    private UUID subjectId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_json", columnDefinition = "jsonb")
    private JsonNode inputJson;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_json", columnDefinition = "jsonb")
    private JsonNode outputJson;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "steps_json", columnDefinition = "jsonb")
    private JsonNode stepsJson;
    @Column(columnDefinition = "text")
    private String error;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;
    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;
    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;
    @Column(name = "model_calls", nullable = false)
    private int modelCalls;
    @Column(name = "minutes_saved", nullable = false)
    private int minutesSaved;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "finished_at")
    private Instant finishedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID subjectId) { this.subjectId = subjectId; }
    public JsonNode getInputJson() { return inputJson; }
    public void setInputJson(JsonNode inputJson) { this.inputJson = inputJson; }
    public JsonNode getOutputJson() { return outputJson; }
    public void setOutputJson(JsonNode outputJson) { this.outputJson = outputJson; }
    public JsonNode getStepsJson() { return stepsJson; }
    public void setStepsJson(JsonNode stepsJson) { this.stepsJson = stepsJson; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public void setLeaseExpiresAt(Instant leaseExpiresAt) { this.leaseExpiresAt = leaseExpiresAt; }
    public Instant getLastHeartbeatAt() { return lastHeartbeatAt; }
    public void setLastHeartbeatAt(Instant lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }
    public int getModelCalls() { return modelCalls; }
    public void setModelCalls(int modelCalls) { this.modelCalls = modelCalls; }
    public int getMinutesSaved() { return minutesSaved; }
    public void setMinutesSaved(int minutesSaved) { this.minutesSaved = minutesSaved; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
