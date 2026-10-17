package com.guangge.Interview.goal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** 计划里的一天一题（今天默认 3 条）。 */
@Entity
@Table(name = "study_plan_items")
public class StudyPlanItemEntity {

    @Id
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "owner_id", nullable = false, length = 160)
    private String ownerId;

    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    @Column(name = "kind", nullable = false, length = 24)
    private String kind;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "question_ref", length = 120)
    private String questionRef;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "estimated_minutes", nullable = false)
    private int estimatedMinutes;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public LocalDate getPlanDate() { return planDate; }
    public void setPlanDate(LocalDate planDate) { this.planDate = planDate; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public String getQuestionRef() { return questionRef; }
    public void setQuestionRef(String questionRef) { this.questionRef = questionRef; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getEstimatedMinutes() { return estimatedMinutes; }
    public void setEstimatedMinutes(int estimatedMinutes) { this.estimatedMinutes = estimatedMinutes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
