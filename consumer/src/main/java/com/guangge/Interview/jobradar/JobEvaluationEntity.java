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
@Table(name = "job_evaluations")
public class JobEvaluationEntity {
    @Id
    private UUID id;
    @Column(name = "owner_id", nullable = false, length = 80)
    private String ownerId;
    @Column(name = "job_lead_id", nullable = false)
    private UUID jobLeadId;
    @Column(name = "jd_hash", nullable = false, length = 64)
    private String jdHash;
    @Column(name = "profile_hash", nullable = false, length = 64)
    private String profileHash;
    @Column(name = "prompt_version", nullable = false, length = 20)
    private String promptVersion;
    @Column(nullable = false)
    private int score;
    @Column(nullable = false, length = 10)
    private String confidence;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "breakdown_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode breakdownJson;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hits_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode hitsJson;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gaps_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode gapsJson;
    @Column(columnDefinition = "text")
    private String reason;
    @Column(nullable = false, length = 20)
    private String recommendation;
    @Column(nullable = false)
    private boolean degraded;
    @Column(length = 80)
    private String model;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public UUID getJobLeadId() { return jobLeadId; }
    public void setJobLeadId(UUID jobLeadId) { this.jobLeadId = jobLeadId; }
    public String getJdHash() { return jdHash; }
    public void setJdHash(String jdHash) { this.jdHash = jdHash; }
    public String getProfileHash() { return profileHash; }
    public void setProfileHash(String profileHash) { this.profileHash = profileHash; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
    public JsonNode getBreakdownJson() { return breakdownJson; }
    public void setBreakdownJson(JsonNode breakdownJson) { this.breakdownJson = breakdownJson; }
    public JsonNode getHitsJson() { return hitsJson; }
    public void setHitsJson(JsonNode hitsJson) { this.hitsJson = hitsJson; }
    public JsonNode getGapsJson() { return gapsJson; }
    public void setGapsJson(JsonNode gapsJson) { this.gapsJson = gapsJson; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public boolean isDegraded() { return degraded; }
    public void setDegraded(boolean degraded) { this.degraded = degraded; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
