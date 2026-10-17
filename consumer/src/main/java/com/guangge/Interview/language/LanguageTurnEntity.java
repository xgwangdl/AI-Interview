package com.guangge.Interview.language;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 一轮对话：用户说的一句 + AI 的回一句 + 这一句的改进建议。
 *
 * 各段耗时都落库（asr/llm/tts/total）：L1 的体感就是由它们决定的，
 * 不记下来就只能凭"感觉有点慢"来优化。
 */
@Entity
@Table(name = "language_turns")
public class LanguageTurnEntity {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "turn_index", nullable = false)
    private int turnIndex;

    @Column(name = "audio_key", length = 100)
    private String audioKey;

    @Column(name = "audio_content_type", length = 100)
    private String audioContentType;

    @Column(name = "audio_ms")
    private Integer audioMs;

    @Column(name = "reply_audio_key", length = 100)
    private String replyAudioKey;

    @Column(name = "transcript", columnDefinition = "text")
    private String transcript;

    @Column(name = "reply_text", columnDefinition = "text")
    private String replyText;

    @Column(name = "feedback_json", columnDefinition = "text")
    private String feedbackJson;

    @Column(name = "asr_ms")
    private Integer asrMs;

    @Column(name = "llm_ms")
    private Integer llmMs;

    @Column(name = "tts_ms")
    private Integer ttsMs;

    @Column(name = "total_ms")
    private Integer totalMs;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public int getTurnIndex() { return turnIndex; }
    public void setTurnIndex(int turnIndex) { this.turnIndex = turnIndex; }
    public String getAudioKey() { return audioKey; }
    public void setAudioKey(String audioKey) { this.audioKey = audioKey; }
    public String getAudioContentType() { return audioContentType; }
    public void setAudioContentType(String audioContentType) { this.audioContentType = audioContentType; }
    public Integer getAudioMs() { return audioMs; }
    public void setAudioMs(Integer audioMs) { this.audioMs = audioMs; }
    public String getReplyAudioKey() { return replyAudioKey; }
    public void setReplyAudioKey(String replyAudioKey) { this.replyAudioKey = replyAudioKey; }
    public String getTranscript() { return transcript; }
    public void setTranscript(String transcript) { this.transcript = transcript; }
    public String getReplyText() { return replyText; }
    public void setReplyText(String replyText) { this.replyText = replyText; }
    public String getFeedbackJson() { return feedbackJson; }
    public void setFeedbackJson(String feedbackJson) { this.feedbackJson = feedbackJson; }
    public Integer getAsrMs() { return asrMs; }
    public void setAsrMs(Integer asrMs) { this.asrMs = asrMs; }
    public Integer getLlmMs() { return llmMs; }
    public void setLlmMs(Integer llmMs) { this.llmMs = llmMs; }
    public Integer getTtsMs() { return ttsMs; }
    public void setTtsMs(Integer ttsMs) { this.ttsMs = ttsMs; }
    public Integer getTotalMs() { return totalMs; }
    public void setTotalMs(Integer totalMs) { this.totalMs = totalMs; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
