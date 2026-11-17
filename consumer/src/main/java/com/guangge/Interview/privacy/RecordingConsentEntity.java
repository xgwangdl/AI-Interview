package com.guangge.Interview.privacy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "privacy_consents", uniqueConstraints = @UniqueConstraint(
        name = "uq_privacy_consents_owner_type_version",
        columnNames = {"owner_id", "consent_type", "version"}))
public class RecordingConsentEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, length = 160)
    private String ownerId;

    @Column(name = "consent_type", nullable = false, length = 64)
    private String consentType;

    @Column(name = "version", nullable = false, length = 64)
    private String version;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @PrePersist
    void applyDefaults() {
        if (id == null) id = UUID.randomUUID();
        if (acceptedAt == null) acceptedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getConsentType() { return consentType; }
    public void setConsentType(String consentType) { this.consentType = consentType; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
}
