package com.guangge.Interview.coding.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 一次提交的判分结果（隐藏用例只回传是否通过，不含输入输出）。 */
public record CodingSubmissionView(
        UUID id,
        UUID questionId,
        String status,
        int passedCases,
        int totalCases,
        Double score,
        Long runtimeMs,
        String stdout,
        String stderr,
        String compileOutput,
        List<CaseResult> cases,
        JudgeOutcome.ShortAnswerReview review,
        String answerText,
        Instant createdAt) {

    /** 0-100：客观题按通过用例比例，简答题直接用模型给的分数。 */
    public static Double scoreOf(String status, int passed, int total, JudgeOutcome.ShortAnswerReview review) {
        if (review != null) return review.score();
        if (total <= 0) return null;
        return Math.round(passed * 1000.0 / total) / 10.0;
    }
}
