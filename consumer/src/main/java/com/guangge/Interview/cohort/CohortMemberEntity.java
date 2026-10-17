package com.guangge.Interview.cohort;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "cohort_members")
@IdClass(CohortMemberEntity.Key.class)
public class CohortMemberEntity {

    @Id
    @Column(name = "cohort_id")
    private UUID cohortId;

    @Id
    @Column(name = "owner_id", length = 160)
    private String ownerId;

    @Column(name = "role", nullable = false, length = 16)
    private String role;

    @Column(name = "share_scores", nullable = false)
    private boolean shareScores;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    public UUID getCohortId() { return cohortId; }
    public void setCohortId(UUID cohortId) { this.cohortId = cohortId; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isShareScores() { return shareScores; }
    public void setShareScores(boolean shareScores) { this.shareScores = shareScores; }
    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }

    public static class Key implements Serializable {
        private UUID cohortId;
        private String ownerId;

        public Key() { }

        public Key(UUID cohortId, String ownerId) {
            this.cohortId = cohortId;
            this.ownerId = ownerId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            return Objects.equals(cohortId, key.cohortId) && Objects.equals(ownerId, key.ownerId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cohortId, ownerId);
        }
    }
}
