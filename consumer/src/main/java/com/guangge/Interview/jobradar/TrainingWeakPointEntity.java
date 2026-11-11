package com.guangge.Interview.jobradar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "training_weak_points")
public class TrainingWeakPointEntity {
    @Id
    private UUID id;
    @Column(name = "owner_id", nullable = false, length = 80)
    private String ownerId;
    @Column(nullable = false, length = 20)
    private String source;
    @Column(name = "debrief_id")
    private UUID debriefId;
    @Column(nullable = false, length = 200)
    private String topic;
    @Column(name = "dimension_key", nullable = false, length = 40)
    private String dimensionKey;
    @Column(columnDefinition = "text")
    private String evidence;
    @Column(nullable = false)
    private int weight;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "closed_at")
    private Instant closedAt;
    @Column(name = "closed_by", length = 20)
    private String closedBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public UUID getDebriefId() { return debriefId; }
    public void setDebriefId(UUID debriefId) { this.debriefId = debriefId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getDimensionKey() { return dimensionKey; }
    public void setDimensionKey(String dimensionKey) { this.dimensionKey = dimensionKey; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public String getClosedBy() { return closedBy; }
    public void setClosedBy(String closedBy) { this.closedBy = closedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
