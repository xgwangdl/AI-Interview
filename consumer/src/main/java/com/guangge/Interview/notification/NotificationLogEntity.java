package com.guangge.Interview.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_log")
public class NotificationLogEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, length = 160)
    private String ownerId;

    @Column(name = "kind", nullable = false, length = 32)
    private String kind;

    @Column(name = "channel", nullable = false, length = 16)
    private String channel;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "error", length = 300)
    private String error;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
