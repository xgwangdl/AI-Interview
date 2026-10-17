package com.guangge.Interview.practice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "practice_answers")
public class PracticeAnswerEntity {
    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "question_index", nullable = false)
    private int questionIndex;

    @Column(name = "question_id", nullable = false, length = 64)
    private String questionId;

    @Column(name = "media_key", length = 100)
    private String mediaKey;

    @Column(name = "media_content_type", length = 100)
    private String mediaContentType;

    @Column(name = "media_size_bytes")
    private Long mediaSizeBytes;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "started_at_offset_seconds")
    private Integer startedAtOffsetSeconds;

    @Column(name = "transcript_status", nullable = false, length = 32)
    private String transcriptStatus;

    @Column(name = "transcript_text", columnDefinition = "text")
    private String transcriptText;

    /** 第一次人工修正前的原转写；用于审计与"为什么分数变了"。 */
    @Column(name = "transcript_original", columnDefinition = "text")
    private String transcriptOriginal;

    @Column(name = "transcript_edited_at")
    private Instant transcriptEditedAt;

    @Column(name = "evaluation_status", nullable = false, length = 32)
    private String evaluationStatus;

    @Column(name = "evaluation_json", columnDefinition = "text")
    private String evaluationJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public PracticeAnswerEntity() { }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public int getQuestionIndex() { return questionIndex; }
    public void setQuestionIndex(int questionIndex) { this.questionIndex = questionIndex; }
    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }
    public String getMediaKey() { return mediaKey; }
    public void setMediaKey(String mediaKey) { this.mediaKey = mediaKey; }
    public String getMediaContentType() { return mediaContentType; }
    public void setMediaContentType(String mediaContentType) { this.mediaContentType = mediaContentType; }
    public Long getMediaSizeBytes() { return mediaSizeBytes; }
    public void setMediaSizeBytes(Long mediaSizeBytes) { this.mediaSizeBytes = mediaSizeBytes; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public Integer getStartedAtOffsetSeconds() { return startedAtOffsetSeconds; }
    public void setStartedAtOffsetSeconds(Integer startedAtOffsetSeconds) { this.startedAtOffsetSeconds = startedAtOffsetSeconds; }
    public String getTranscriptStatus() { return transcriptStatus; }
    public void setTranscriptStatus(String transcriptStatus) { this.transcriptStatus = transcriptStatus; }
    public String getTranscriptText() { return transcriptText; }
    public void setTranscriptText(String transcriptText) { this.transcriptText = transcriptText; }
    public String getTranscriptOriginal() { return transcriptOriginal; }
    public void setTranscriptOriginal(String transcriptOriginal) { this.transcriptOriginal = transcriptOriginal; }
    public Instant getTranscriptEditedAt() { return transcriptEditedAt; }
    public void setTranscriptEditedAt(Instant transcriptEditedAt) { this.transcriptEditedAt = transcriptEditedAt; }
    public String getEvaluationStatus() { return evaluationStatus; }
    public void setEvaluationStatus(String evaluationStatus) { this.evaluationStatus = evaluationStatus; }
    public String getEvaluationJson() { return evaluationJson; }
    public void setEvaluationJson(String evaluationJson) { this.evaluationJson = evaluationJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
