package com.guangge.Interview.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvaluationResultValidatorTest {

    private final EvaluationResultValidator validator =
            new EvaluationResultValidator(new ObjectMapper().findAndRegisterModules());

    @Test
    void acceptsAWellFormedEvaluation() {
        EvaluationResult result = validator.parse(validJson());

        assertEquals(82.5, result.overallScore(), 0.001);
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
    void rejectsInvalidEvidenceTimeWindow() {
        String json = validJson().replace("\"startSeconds\": 0, \"endSeconds\": 5",
                "\"startSeconds\": 9, \"endSeconds\": 4");

        assertThrows(EvaluationFormatException.class, () -> validator.parse(json));
    }

    @Test
    void rejectsFreeFormModelOutput() {
        assertThrows(EvaluationFormatException.class, () -> validator.parse("这个回答整体还不错，可以再加强 STAR 结构。"));
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
                  "improvementSuggestions": ["多用 STAR 结构描述项目"],
                  "sampleAnswer": "先讲背景，再讲任务与行动。",
                  "evidence": [
                    { "dimension": "relevance", "quote": "我主导了订单系统的重构", "startSeconds": 0, "endSeconds": 5 }
                  ]
                }
                """;
    }
}
