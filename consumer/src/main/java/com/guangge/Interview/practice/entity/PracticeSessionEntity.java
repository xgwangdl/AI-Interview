package com.guangge.Interview.practice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "practice_sessions")
public class PracticeSessionEntity {
    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, length = 160)
    private String ownerId;

    /** 关联的求职目标；为空表示快速练习。 */
    @Column(name = "goal_id")
    private UUID goalId;

    @Column(name = "role", nullable = false, length = 160)
    private String role;

    @Column(name = "company", length = 200)
    private String company;

    @Column(name = "job_description", columnDefinition = "text")
    private String jobDescription;

    @Column(name = "resume_name", length = 255)
    private String resumeName;

    @Column(name = "interview_type", nullable = false, length = 32)
    private String interviewType;

    @Column(name = "difficulty", nullable = false, length = 32)
    private String difficulty;

    @Column(name = "interviewer_style", nullable = false, length = 32)
    private String interviewerStyle;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "questions_json", nullable = false, columnDefinition = "text")
    private String questionsJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "retention_until", nullable = false)
    private Instant retentionUntil;

    /** 是否允许面试官根据回答追问（V9 起）。追问要多一次转写 + 一次模型调用，所以在创建时由用户决定。 */
    @Column(name = "follow_up_enabled", nullable = false)
    private boolean followUpEnabled;

    /** per_question / continuous（Phase 2）。 */
    @Column(name = "mode", nullable = false, length = 16)
    private String mode = "per_question";

    /** 连续面试允许暂停一次；这里记录实际暂停次数。 */
    @Column(name = "paused_count", nullable = false)
    private int pausedCount;

    public PracticeSessionEntity() { }

    public boolean isFollowUpEnabled() { return followUpEnabled; }
    public void setFollowUpEnabled(boolean followUpEnabled) { this.followUpEnabled = followUpEnabled; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public int getPausedCount() { return pausedCount; }
    public void setPausedCount(int pausedCount) { this.pausedCount = pausedCount; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public UUID getGoalId() { return goalId; }
    public void setGoalId(UUID goalId) { this.goalId = goalId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getJobDescription() { return jobDescription; }
    public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }
    public String getResumeName() { return resumeName; }
    public void setResumeName(String resumeName) { this.resumeName = resumeName; }
    public String getInterviewType() { return interviewType; }
    public void setInterviewType(String interviewType) { this.interviewType = interviewType; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getInterviewerStyle() { return interviewerStyle; }
    public void setInterviewerStyle(String interviewerStyle) { this.interviewerStyle = interviewerStyle; }
    public int getQuestionCount() { return questionCount; }
    public void setQuestionCount(int questionCount) { this.questionCount = questionCount; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public String getQuestionsJson() { return questionsJson; }
    public void setQuestionsJson(String questionsJson) { this.questionsJson = questionsJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getRetentionUntil() { return retentionUntil; }
    public void setRetentionUntil(Instant retentionUntil) { this.retentionUntil = retentionUntil; }
}
