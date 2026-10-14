package com.guangge.Interview.coding.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 一次笔试练习的视图：题目 + 每题最近一次提交的概要。 */
public record CodingSessionView(
        UUID id,
        String role,
        String company,
        String difficulty,
        String status,
        Instant createdAt,
        Instant retentionUntil,
        List<CodeQuestionView> questions,
        List<SubmissionSummary> submissions) {

    /** 只给列表用的轻量状态，不带代码与用例细节。 */
    public record SubmissionSummary(
            UUID questionId,
            String status,
            int passedCases,
            int totalCases,
            Double score,
            Instant submittedAt) {
    }
}
