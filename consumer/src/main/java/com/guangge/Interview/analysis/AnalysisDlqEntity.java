package com.guangge.Interview.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 死信队列条目：任务重试到上限后转存到这里，供人工排查与重新入队。
 *
 * id 沿用原 analysis_jobs 的主键，因此「重新入队」就是把它对应的任务改回 pending。
 */
@Entity
@Table(name = "analysis_jobs_dlq")
public class AnalysisDlqEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "answer_id", nullable = false)
    private UUID answerId;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "original_created_at", nullable = false)
    private Instant originalCreatedAt;

    @Column(name = "moved_at", nullable = false)
    private Instant movedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public UUID getAnswerId() { return answerId; }
    public void setAnswerId(UUID answerId) { this.answerId = answerId; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public Instant getOriginalCreatedAt() { return originalCreatedAt; }
    public void setOriginalCreatedAt(Instant originalCreatedAt) { this.originalCreatedAt = originalCreatedAt; }

    public Instant getMovedAt() { return movedAt; }
    public void setMovedAt(Instant movedAt) { this.movedAt = movedAt; }
}
