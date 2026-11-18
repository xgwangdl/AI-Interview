package com.guangge.Interview.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationResultValidatorTest {

    private final EvaluationResultValidator validator =
            new EvaluationResultValidator(new ObjectMapper().findAndRegisterModules());

    @Test
    void acceptsAWellFormedEvaluation() {
        EvaluationResult result = validator.parse(validJson());

        assertEquals(82.5, result.overallScore(), 0.001);
        assertEquals(5, result.dimensionFeedback().size());
        assertEquals(1, result.improvementSuggestions().size());
        assertEquals(1, result.evidence().size());
    }

    @Test
    void stripsMarkdownCodeFences() {
        EvaluationResult result = validator.parse("```json\n" + validJson() + "\n```");

        assertEquals(82.5, result.overallScore(), 0.001);
    }

    @Test
    void rejectsScoresOutsideTheAllowedRange() {
        String json = validJson().replace("\"overallScore\": 82.5", "\"overallScore\": 140");

        assertThrows(EvaluationFormatException.class, () -> validator.parse(json));
    }

    @Test
    void rejectsMissingImprovementSuggestions() {
        String json = validJson().replace("[\"多用 STAR 结构描述项目\"]", "[]");

        assertThrows(EvaluationFormatException.class, () -> validator.parse(json));
    }

    @Test
    void rejectsNewEvaluationsWithoutDimensionReasons() {
        String json = validJson().replace("""
                  "dimensionFeedback": [
                    {"dimension": "relevance", "reason": "回答直接回应了重构目标。", "evidenceQuote": "我主导了订单系统的重构"},
                    {"dimension": "completeness", "reason": "有背景、行动和结果。", "evidenceQuote": "我主导了订单系统的重构"},
                    {"dimension": "professionalism", "reason": "使用了具体工程术语。", "evidenceQuote": "我主导了订单系统的重构"},
                    {"dimension": "starStructure", "reason": "行动和结果可以更明确。", "evidenceQuote": "我主导了订单系统的重构"},
                    {"dimension": "logic", "reason": "先讲目标再讲行动。", "evidenceQuote": "我主导了订单系统的重构"}
                  ],
                """, "");

        EvaluationFormatException exception = assertThrows(EvaluationFormatException.class,
                () -> validator.parse(json));

        assertEquals("dimensionFeedback is required", exception.getMessage());
    }

    @Test
    void rejectsDimensionEvidenceThatWasNotSaidInTheTranscript() {
        String json = validJson().replace(
                "\"reason\": \"回答直接回应了重构目标。\", \"evidenceQuote\": \"我主导了订单系统的重构\"",
                "\"reason\": \"回答直接回应了重构目标。\", \"evidenceQuote\": \"我设计了整个支付平台\"");

        EvaluationFormatException exception = assertThrows(EvaluationFormatException.class,
                () -> validator.parse(json, "我主导了订单系统的重构，并补充了索引。"));

        assertTrue(exception.getMessage().contains("dimensionFeedback.evidenceQuote"));
    }

    @Test
    void acceptsEvidenceWithoutInventedTimestamps() {
        String json = validJson()
                .replace("\"startSeconds\": 0, \"endSeconds\": 5",
                        "\"startSeconds\": null, \"endSeconds\": null");

        EvaluationResult result = validator.parse(json, "我主导了订单系统的重构");

        assertEquals(null, result.evidence().get(0).startSeconds());
        assertEquals(null, result.evidence().get(0).endSeconds());
    }

    @Test
    void rejectsInvalidEvidenceTimeWindow() {
        String json = validJson().replace("\"startSeconds\": 0, \"endSeconds\": 5",
                "\"startSeconds\": 9, \"endSeconds\": 4");

        assertThrows(EvaluationFormatException.class, () -> validator.parse(json));
    }

    /**
     * 缺字段不能被当成 0 分：record 在过去用的是原始类型，Jackson 会把缺失的数值填成 0，
     * 而 0 是合法分数，范围校验拦不住，于是模型漏字段就变成静默的 0 分。
     */
    @Test
    void rejectsMissingDimensionInsteadOfSilentlyScoringZero() {
        String json = validJson().replace("\"logic\": 84", "\"logicIgnored\": 84");

        EvaluationFormatException exception = assertThrows(EvaluationFormatException.class,
                () -> validator.parse(json));
        assertEquals("scores.logic is required", exception.getMessage());
    }

    @Test
    void rejectsNullDimensionInsteadOfSilentlyScoringZero() {
        String json = validJson().replace("\"logic\": 84", "\"logic\": null");

        EvaluationFormatException exception = assertThrows(EvaluationFormatException.class,
                () -> validator.parse(json));
        assertEquals("scores.logic is required", exception.getMessage());
    }

    @Test
    void stillAcceptsAnExplicitAllZeroResult() {
        // 0 分本身是合法的：字段齐全时不能因为「都是 0」就拒绝，那是模型的真实判断。
        String json = validJson()
                .replace("\"overallScore\": 82.5", "\"overallScore\": 0")
                .replace("\"relevance\": 88", "\"relevance\": 0")
                .replace("\"completeness\": 80", "\"completeness\": 0")
                .replace("\"professionalism\": 85", "\"professionalism\": 0")
                .replace("\"starStructure\": 75", "\"starStructure\": 0")
                .replace("\"logic\": 84", "\"logic\": 0");

        EvaluationResult result = validator.parse(json);

        assertEquals(0.0, result.overallScore(), 0.001);
        assertEquals(0.0, result.scores().logic(), 0.001);
    }

    @Test
    void rejectsFreeFormModelOutput() {
        assertThrows(EvaluationFormatException.class, () -> validator.parse("这个回答整体还不错，可以再加强 STAR 结构。"));
    }

    @Test
    void rejectsSampleAnswerNumbersThatWereNotInTheTranscript() {
        String json = validJson().replace(
                "\"sampleAnswer\": \"先讲背景，再讲任务与行动。\"",
                "\"sampleAnswer\": \"先讲背景，再说我把接口耗时降低了 25%。\"");

        EvaluationFormatException exception = assertThrows(EvaluationFormatException.class,
                () -> validator.parse(json, "我主导了订单系统的重构。我负责订单系统，先讲背景再讲行动。"));

        assertEquals("sampleAnswer contains numbers not present in the transcript: 25%", exception.getMessage());
    }

    @Test
    void allowsGroundedSampleNumbersAndNumbersInsidePlaceholders() {
        String json = validJson().replace(
                "\"sampleAnswer\": \"先讲背景，再讲任务与行动。\"",
                "\"sampleAnswer\": \"接口从 900ms 降到 100ms，【请补充：2026 年数据】。\"");

        EvaluationResult result = validator.parse(json, "我主导了订单系统的重构。接口从 900ms 降到 100ms。");

        assertEquals(82.5, result.overallScore(), 0.001);
    }

    static String validJson() {
        return """
                {
                  "overallScore": 82.5,
                  "scores": {
                    "relevance": 88,
                    "completeness": 80,
                    "professionalism": 85,
                    "starStructure": 75,
                    "logic": 84
                  },
                  "delivery": {
                    "speechRateCharactersPerMinute": 210.5,
                    "pauseCount": 4,
                    "totalPauseSeconds": 7.5,
                    "fillerWordCount": 3
                  },
                  "dimensionFeedback": [
                    {"dimension": "relevance", "reason": "回答直接回应了重构目标。", "evidenceQuote": "我主导了订单系统的重构"},
                    {"dimension": "completeness", "reason": "有背景、行动和结果。", "evidenceQuote": "我主导了订单系统的重构"},
                    {"dimension": "professionalism", "reason": "使用了具体工程术语。", "evidenceQuote": "我主导了订单系统的重构"},
                    {"dimension": "starStructure", "reason": "行动和结果可以更明确。", "evidenceQuote": "我主导了订单系统的重构"},
                    {"dimension": "logic", "reason": "先讲目标再讲行动。", "evidenceQuote": "我主导了订单系统的重构"}
                  ],
                  "improvementSuggestions": ["多用 STAR 结构描述项目"],
                  "sampleAnswer": "先讲背景，再讲任务与行动。",
                  "evidence": [
                    { "dimension": "relevance", "quote": "我主导了订单系统的重构", "startSeconds": 0, "endSeconds": 5 }
                  ]
                }
                """;
    }
}
