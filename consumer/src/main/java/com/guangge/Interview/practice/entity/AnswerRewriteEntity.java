package com.guangge.Interview.practice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** 改写历史：AI 生成与用户编辑都留一条，便于对照。 */
@Entity
@Table(name = "answer_rewrites")
public class AnswerRewriteEntity {

    @Id
    private UUID id;

    @Column(name = "answer_id", nullable = false)
    private UUID answerId;

    @Column(name = "owner_id", nullable = false, length = 160)
    private String ownerId;

    @Column(name = "rewrite_text", nullable = false, columnDefinition = "text")
    private String rewriteText;

    @Column(name = "source", nullable = false, length = 16)
    private String source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAnswerId() { return answerId; }
    public void setAnswerId(UUID answerId) { this.answerId = answerId; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getRewriteText() { return rewriteText; }
    public void setRewriteText(String rewriteText) { this.rewriteText = rewriteText; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
