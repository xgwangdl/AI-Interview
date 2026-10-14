package com.guangge.Interview.coding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** 一次作答与判分结果。 */
@Entity
@Table(name = "code_submissions")
@Getter
@Setter
public class CodeSubmissionEntity {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "owner_id", nullable = false, length = 255)
    private String ownerId;

    /** 代码 / SQL / 选项 key / 简答文本。 */
    @Column(name = "answer_text", columnDefinition = "text")
    private String answerText;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "passed_cases", nullable = false)
    private int passedCases;

    @Column(name = "total_cases", nullable = false)
    private int totalCases;

    @Column(name = "runtime_ms")
    private Long runtimeMs;

    @Column(columnDefinition = "text")
    private String stdout;

    @Column(columnDefinition = "text")
    private String stderr;

    @Column(name = "compile_output", columnDefinition = "text")
    private String compileOutput;

    @Column(name = "case_results_json", columnDefinition = "text")
    private String caseResultsJson;

    @Column(name = "ai_review_json", columnDefinition = "text")
    private String aiReviewJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
