package com.guangge.Interview.coding.dto;

import java.util.List;

/** 一次判分的汇总结果。 */
public record JudgeOutcome(
        String status,
        int passedCases,
        int totalCases,
        Long runtimeMs,
        String stdout,
        String stderr,
        String compileOutput,
        List<CaseResult> cases,
        ShortAnswerReview review) {

    public static final String PASSED = "passed";
    public static final String FAILED = "failed";
    public static final String ERROR = "error";

    /** 简答题没有「用例」，只有模型按要点给出的评分。 */
    public record ShortAnswerReview(double score, String summary, List<String> matched, List<String> missing) { }

    public static JudgeOutcome of(String status, int passed, int total, Long runtimeMs,
                                  String stdout, String stderr, String compileOutput, List<CaseResult> cases) {
        return new JudgeOutcome(status, passed, total, runtimeMs, stdout, stderr, compileOutput, cases, null);
    }
}
