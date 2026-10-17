package com.guangge.Interview.goal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** 一个真实求职目标：公司 + 岗位 + 轮次 + 面试时间。 */
@Entity
@Table(name = "job_goals")
public class JobGoalEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, length = 160)
    private String ownerId;

    @Column(name = "company", length = 200)
    private String company;

    @Column(name = "role", nullable = false, length = 160)
    private String role;

    @Column(name = "job_description", columnDefinition = "text")
    private String jobDescription;

    @Column(name = "resume_id")
    private UUID resumeId;

    @Column(name = "stage", nullable = false, length = 32)
    private String stage;

    @Column(name = "interview_at")
    private Instant interviewAt;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getJobDescription() { return jobDescription; }
    public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }
    public UUID getResumeId() { return resumeId; }
    public void setResumeId(UUID resumeId) { this.resumeId = resumeId; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public Instant getInterviewAt() { return interviewAt; }
    public void setInterviewAt(Instant interviewAt) { this.interviewAt = interviewAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
}
