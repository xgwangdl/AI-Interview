package com.guangge.Interview.resume;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** 一份简历。只存正文与版本，不做结构化分栏（见需求文档 Q5）。 */
@Entity
@Table(name = "resumes")
@Getter
@Setter
public class ResumeEntity {

    public static final String SOURCE_UPLOAD = "upload";
    public static final String SOURCE_MANUAL = "manual";

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, length = 255)
    private String ownerId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "raw_text", nullable = false, columnDefinition = "text")
    private String rawText;

    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
