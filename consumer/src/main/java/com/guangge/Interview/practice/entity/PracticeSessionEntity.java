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

    public PracticeSessionEntity() { }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
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
