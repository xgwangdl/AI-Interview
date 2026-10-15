package com.guangge.Interview.resume;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** 一次优化。保留历史，便于回看与逐段采纳。 */
@Entity
@Table(name = "resume_optimizations")
@Getter
@Setter
public class ResumeOptimizationEntity {

    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";

    @Id
    private UUID id;

    @Column(name = "resume_id", nullable = false)
    private UUID resumeId;

    @Column(name = "owner_id", nullable = false, length = 255)
    private String ownerId;

    @Column(length = 200)
    private String role;

    @Column(name = "job_description", columnDefinition = "text")
    private String jobDescription;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "optimized_text", columnDefinition = "text")
    private String optimizedText;

    @Column(name = "changes_json", columnDefinition = "text")
    private String changesJson;

    @Column(name = "placeholders_json", columnDefinition = "text")
    private String placeholdersJson;

    @Column(columnDefinition = "text")
    private String error;

    @Column(length = 64)
    private String model;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
