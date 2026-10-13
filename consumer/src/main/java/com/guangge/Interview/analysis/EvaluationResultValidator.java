package com.guangge.Interview.analysis;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.util.JsonText;
import org.springframework.stereotype.Component;

@Component
public class EvaluationResultValidator {

    private final ObjectMapper objectMapper;

    public EvaluationResultValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /** Parses raw model text into the fixed evaluation schema, rejecting anything else. */
    public EvaluationResult parse(String raw) {
        return validate(deserialize(raw, EvaluationResult.class));
    }

    public EvaluationReport parseReport(String raw) {
        EvaluationReport report = deserialize(raw, EvaluationReport.class);
        if (report == null) throw new EvaluationFormatException("Stored evaluation report is empty");
        validate(report.result());
        return report;
    }

    public String write(EvaluationReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception exception) {
            throw new EvaluationFormatException("Unable to serialize the evaluation report", exception);
        }
    }

    public EvaluationResult validate(EvaluationResult result) {
        if (result == null) throw new EvaluationFormatException("Evaluation is missing");
        if (result.scores() == null) throw new EvaluationFormatException("scores are required");
        bounded(result.overallScore(), "overallScore");
        bounded(result.scores().relevance(), "scores.relevance");
        bounded(result.scores().completeness(), "scores.completeness");
        bounded(result.scores().professionalism(), "scores.professionalism");
        bounded(result.scores().starStructure(), "scores.starStructure");
        bounded(result.scores().logic(), "scores.logic");

        if (result.delivery() == null) throw new EvaluationFormatException("delivery metrics are required");
        nonNegative(result.delivery().speechRateCharactersPerMinute(), "delivery.speechRateCharactersPerMinute");
        nonNegative(result.delivery().pauseCount(), "delivery.pauseCount");
        nonNegative(result.delivery().totalPauseSeconds(), "delivery.totalPauseSeconds");
        nonNegative(result.delivery().fillerWordCount(), "delivery.fillerWordCount");

        if (result.improvementSuggestions() == null || result.improvementSuggestions().isEmpty()) {
            throw new EvaluationFormatException("improvementSuggestions are required");
        }
        if (result.sampleAnswer() == null || result.sampleAnswer().isBlank()) {
            throw new EvaluationFormatException("sampleAnswer is required");
        }
        if (result.evidence() != null) {
            for (EvaluationResult.Evidence evidence : result.evidence()) {
                if (evidence == null) throw new EvaluationFormatException("evidence entries must not be null");
                if (evidence.dimension() == null || evidence.dimension().isBlank()) {
                    throw new EvaluationFormatException("evidence.dimension is required");
                }
                if (evidence.startSeconds() == null || evidence.endSeconds() == null
                        || evidence.startSeconds() < 0 || evidence.endSeconds() < evidence.startSeconds()) {
                    throw new EvaluationFormatException("evidence time range is invalid");
                }
            }
        }
        return result;
    }

    private <T> T deserialize(String raw, Class<T> type) {
        if (raw == null || raw.isBlank()) throw new EvaluationFormatException("Model returned an empty evaluation");
        try {
            return objectMapper.readValue(JsonText.stripCodeFence(raw), type);
        } catch (EvaluationFormatException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new EvaluationFormatException("Response is not valid evaluation JSON", exception);
        }
    }

    private static void bounded(Double value, String field) {
        if (value == null) throw new EvaluationFormatException(field + " is required");
        if (Double.isNaN(value) || value < 0 || value > 100) {
            throw new EvaluationFormatException(field + " must be between 0 and 100");
        }
    }

    private static void nonNegative(Integer value, String field) {
        if (value == null) throw new EvaluationFormatException(field + " is required");
        if (value < 0) throw new EvaluationFormatException(field + " must not be negative");
    }

    private static void nonNegative(Double value, String field) {
        if (value == null) throw new EvaluationFormatException(field + " is required");
        if (Double.isNaN(value) || value < 0) {
            throw new EvaluationFormatException(field + " must not be negative");
        }
    }
}
