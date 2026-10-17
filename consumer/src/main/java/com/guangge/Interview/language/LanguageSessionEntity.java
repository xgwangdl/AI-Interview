package com.guangge.Interview.language;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** 一场外语口语会话（开场白 + 若干轮对话）。 */
@Entity
@Table(name = "language_sessions")
public class LanguageSessionEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, length = 160)
    private String ownerId;

    @Column(name = "language", nullable = false, length = 8)
    private String language;

    @Column(name = "scenario", nullable = false, length = 32)
    private String scenario;

    @Column(name = "level", nullable = false, length = 16)
    private String level;

    @Column(name = "target", length = 200)
    private String target;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "turn_count", nullable = false)
    private int turnCount;

    @Column(name = "max_turns", nullable = false)
    private int maxTurns;

    @Column(name = "report_json", columnDefinition = "text")
    private String reportJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "retention_until", nullable = false)
    private Instant retentionUntil;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getTurnCount() { return turnCount; }
    public void setTurnCount(int turnCount) { this.turnCount = turnCount; }
    public int getMaxTurns() { return maxTurns; }
    public void setMaxTurns(int maxTurns) { this.maxTurns = maxTurns; }
    public String getReportJson() { return reportJson; }
    public void setReportJson(String reportJson) { this.reportJson = reportJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public Instant getRetentionUntil() { return retentionUntil; }
    public void setRetentionUntil(Instant retentionUntil) { this.retentionUntil = retentionUntil; }
}
