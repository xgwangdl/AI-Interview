package com.guangge.Interview.coding.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 笔试报告：逐题结果 + 总体通过情况。 */
public record CodingReportView(
        UUID sessionId,
        String role,
        String status,
        int totalQuestions,
        int answeredQuestions,
        int passedQuestions,
        Double averageScore,
        Instant generatedAt,
        List<QuestionReport> questions) {

    public record QuestionReport(
            UUID questionId,
            int position,
            String type,
            String language,
            String title,
            String statement,
            String status,
            int passedCases,
            int totalCases,
            Double score,
            Long runtimeMs,
            String answerText,
            JudgeOutcome.ShortAnswerReview review,
            Instant submittedAt) {
    }
}
