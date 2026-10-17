package com.guangge.Interview.cohort;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "peer_reviews")
public class PeerReviewEntity {

    @Id
    private UUID id;

    @Column(name = "cohort_id", nullable = false)
    private UUID cohortId;

    @Column(name = "reviewer_id", nullable = false, length = 160)
    private String reviewerId;

    @Column(name = "target_owner_id", nullable = false, length = 160)
    private String targetOwnerId;

    @Column(name = "answer_id", nullable = false)
    private UUID answerId;

    @Column(name = "clarity", nullable = false)
    private int clarity;

    @Column(name = "relevance", nullable = false)
    private int relevance;

    @Column(name = "depth", nullable = false)
    private int depth;

    @Column(name = "suggestion", nullable = false, columnDefinition = "text")
    private String suggestion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCohortId() { return cohortId; }
    public void setCohortId(UUID cohortId) { this.cohortId = cohortId; }
    public String getReviewerId() { return reviewerId; }
    public void setReviewerId(String reviewerId) { this.reviewerId = reviewerId; }
    public String getTargetOwnerId() { return targetOwnerId; }
    public void setTargetOwnerId(String targetOwnerId) { this.targetOwnerId = targetOwnerId; }
    public UUID getAnswerId() { return answerId; }
    public void setAnswerId(UUID answerId) { this.answerId = answerId; }
    public int getClarity() { return clarity; }
    public void setClarity(int clarity) { this.clarity = clarity; }
    public int getRelevance() { return relevance; }
    public void setRelevance(int relevance) { this.relevance = relevance; }
    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
