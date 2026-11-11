package com.guangge.Interview.jobradar;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "interview_debriefs")
public class InterviewDebriefEntity {
    @Id
    private UUID id;
    @Column(name = "owner_id", nullable = false, length = 80)
    private String ownerId;
    @Column(name = "job_lead_id")
    private UUID jobLeadId;
    @Column(name = "practice_session_id")
    private UUID practiceSessionId;
    @Column(name = "interview_date", nullable = false)
    private LocalDate interviewDate;
    @Column(name = "round_name", nullable = false, length = 30)
    private String roundName;
    @Column(nullable = false, length = 20)
    private String outcome;
    @Column(name = "raw_text", nullable = false, columnDefinition = "text")
    private String rawText;
    @Column(name = "raw_text_sha256", nullable = false, length = 64)
    private String rawTextSha256;
    @Column(name = "raw_text_length", nullable = false)
    private int rawTextLength;
    @Column(name = "privacy_acknowledged_at", nullable = false)
    private Instant privacyAcknowledgedAt;
    @Column(name = "interviewer_focus", length = 200)
    private String interviewerFocus;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "questions_json", columnDefinition = "jsonb")
    private JsonNode questionsJson;
    @Column(name = "parse_status", nullable = false, length = 20)
    private String parseStatus;
    @Column(name = "self_notes", columnDefinition = "text")
    private String selfNotes;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public UUID getJobLeadId() { return jobLeadId; }
    public void setJobLeadId(UUID jobLeadId) { this.jobLeadId = jobLeadId; }
    public UUID getPracticeSessionId() { return practiceSessionId; }
    public void setPracticeSessionId(UUID practiceSessionId) { this.practiceSessionId = practiceSessionId; }
    public LocalDate getInterviewDate() { return interviewDate; }
    public void setInterviewDate(LocalDate interviewDate) { this.interviewDate = interviewDate; }
    public String getRoundName() { return roundName; }
    public void setRoundName(String roundName) { this.roundName = roundName; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }
    public String getRawTextSha256() { return rawTextSha256; }
    public void setRawTextSha256(String rawTextSha256) { this.rawTextSha256 = rawTextSha256; }
    public int getRawTextLength() { return rawTextLength; }
    public void setRawTextLength(int rawTextLength) { this.rawTextLength = rawTextLength; }
    public Instant getPrivacyAcknowledgedAt() { return privacyAcknowledgedAt; }
    public void setPrivacyAcknowledgedAt(Instant privacyAcknowledgedAt) { this.privacyAcknowledgedAt = privacyAcknowledgedAt; }
    public String getInterviewerFocus() { return interviewerFocus; }
    public void setInterviewerFocus(String interviewerFocus) { this.interviewerFocus = interviewerFocus; }
    public JsonNode getQuestionsJson() { return questionsJson; }
    public void setQuestionsJson(JsonNode questionsJson) { this.questionsJson = questionsJson; }
    public String getParseStatus() { return parseStatus; }
    public void setParseStatus(String parseStatus) { this.parseStatus = parseStatus; }
    public String getSelfNotes() { return selfNotes; }
    public void setSelfNotes(String selfNotes) { this.selfNotes = selfNotes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
