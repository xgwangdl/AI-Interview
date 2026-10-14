package com.guangge.Interview.coding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** 一次笔试练习（独立于视频面试）。 */
@Entity
@Table(name = "coding_sessions")
@Getter
@Setter
public class CodingSessionEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, length = 255)
    private String ownerId;

    @Column(length = 255)
    private String role;

    @Column(length = 255)
    private String company;

    @Column(name = "job_description", columnDefinition = "text")
    private String jobDescription;

    @Column(length = 32)
    private String difficulty;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "question_ids_json", nullable = false, columnDefinition = "text")
    private String questionIdsJson;

    @Column(name = "retention_until")
    private Instant retentionUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
