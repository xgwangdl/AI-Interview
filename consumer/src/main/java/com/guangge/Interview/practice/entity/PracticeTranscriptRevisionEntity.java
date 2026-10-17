package com.guangge.Interview.practice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** 转写修正审计：每次人工修改都留一条，最多 3 条/题。 */
@Entity
@Table(name = "answer_transcript_revisions")
public class PracticeTranscriptRevisionEntity {

    @Id
    private UUID id;

    @Column(name = "answer_id", nullable = false)
    private UUID answerId;

    @Column(name = "owner_id", nullable = false, length = 160)
    private String ownerId;

    @Column(name = "before_text", columnDefinition = "text")
    private String beforeText;

    @Column(name = "after_text", nullable = false, columnDefinition = "text")
    private String afterText;

    @Column(name = "edited_at", nullable = false)
    private Instant editedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAnswerId() { return answerId; }
    public void setAnswerId(UUID answerId) { this.answerId = answerId; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getBeforeText() { return beforeText; }
    public void setBeforeText(String beforeText) { this.beforeText = beforeText; }
    public String getAfterText() { return afterText; }
    public void setAfterText(String afterText) { this.afterText = afterText; }
    public Instant getEditedAt() { return editedAt; }
    public void setEditedAt(Instant editedAt) { this.editedAt = editedAt; }
}
