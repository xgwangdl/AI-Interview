package com.guangge.Interview.analysis;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.util.JsonText;
import com.guangge.Interview.util.NumberClaims;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class EvaluationResultValidator {

    private static final Set<String> DIMENSIONS = Set.of(
            "relevance", "completeness", "professionalism", "starStructure", "logic");

    private final ObjectMapper objectMapper;

    public EvaluationResultValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /** Parses raw model text into the fixed evaluation schema, rejecting anything else. */
    public EvaluationResult parse(String raw) {
        return parse(raw, null);
    }

    public EvaluationResult parse(String raw, String groundedTranscript) {
        return validate(deserialize(raw, EvaluationResult.class), groundedTranscript, true);
    }

    public EvaluationReport parseReport(String raw) {
        EvaluationReport report = deserialize(raw, EvaluationReport.class);
        if (report == null) throw new EvaluationFormatException("Stored evaluation report is empty");
        // Reports created before dimension reasons were introduced must remain readable.
        validate(report.result(), null, false);
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
        return validate(result, null, true);
    }

    public EvaluationResult validate(EvaluationResult result, String groundedTranscript) {
        return validate(result, groundedTranscript, true);
    }

    private EvaluationResult validate(
            EvaluationResult result, String groundedTranscript, boolean requireDimensionFeedback) {
        if (result == null) throw new EvaluationFormatException("Evaluation is missing");
        if (result.scores() == null) throw new EvaluationFormatException("scores are required");
        bounded(result.overallScore(), "overallScore");
        bounded(result.scores().relevance(), "scores.relevance");
        bounded(result.scores().completeness(), "scores.completeness");
        bounded(result.scores().professionalism(), "scores.professionalism");
        bounded(result.scores().starStructure(), "scores.starStructure");
        bounded(result.scores().logic(), "scores.logic");

        if (result.delivery() != null) {
            nonNegative(result.delivery().speechRateCharactersPerMinute(),
                    "delivery.speechRateCharactersPerMinute");
            nonNegative(result.delivery().pauseCount(), "delivery.pauseCount");
            nonNegative(result.delivery().totalPauseSeconds(), "delivery.totalPauseSeconds");
            nonNegative(result.delivery().fillerWordCount(), "delivery.fillerWordCount");
        }

        if (result.improvementSuggestions() == null || result.improvementSuggestions().isEmpty()) {
            throw new EvaluationFormatException("improvementSuggestions are required");
        }
        if (result.sampleAnswer() == null || result.sampleAnswer().isBlank()) {
            throw new EvaluationFormatException("sampleAnswer is required");
        }
        if (groundedTranscript != null) {
            Set<String> unsupported = NumberClaims.unsupported(
                    NumberClaims.withoutBracketedPlaceholders(result.sampleAnswer()),
                    groundedTranscript);
            if (!unsupported.isEmpty()) {
                throw new EvaluationFormatException("sampleAnswer contains numbers not present in the transcript: "
                        + String.join(", ", unsupported));
            }
        }
        validateDimensionFeedback(result, groundedTranscript, requireDimensionFeedback);
        validateEvidence(result, groundedTranscript);
        return result;
    }

    private static void validateDimensionFeedback(
            EvaluationResult result, String groundedTranscript, boolean required) {
        List<EvaluationResult.DimensionFeedback> feedback = result.dimensionFeedback();
        if (!required && (feedback == null || feedback.isEmpty())) return;
        if (feedback == null || feedback.isEmpty()) {
            throw new EvaluationFormatException("dimensionFeedback is required");
        }

        Set<String> seen = new HashSet<>();
        for (EvaluationResult.DimensionFeedback item : feedback) {
            if (item == null) {
                throw new EvaluationFormatException("dimensionFeedback entries must not be null");
            }
            if (!DIMENSIONS.contains(item.dimension())) {
                throw new EvaluationFormatException("dimensionFeedback.dimension is unsupported");
            }
            if (!seen.add(item.dimension())) {
                throw new EvaluationFormatException("dimensionFeedback.dimension must be unique");
            }
            if (item.reason() == null || item.reason().isBlank()) {
                throw new EvaluationFormatException("dimensionFeedback.reason is required");
            }
            if (result.overallScore() > 30) {
                if (item.evidenceQuote() == null || item.evidenceQuote().isBlank()) {
                    throw new EvaluationFormatException("dimensionFeedback.evidenceQuote is required");
                }
                if (groundedTranscript != null
                        && !compact(groundedTranscript).contains(compact(item.evidenceQuote()))) {
                    throw new EvaluationFormatException(
                            "dimensionFeedback.evidenceQuote is not present in the transcript");
                }
            }
        }
        if (required && !seen.equals(DIMENSIONS)) {
            Set<String> missing = new HashSet<>(DIMENSIONS);
            missing.removeAll(seen);
            throw new EvaluationFormatException(
                    "dimensionFeedback is missing dimensions: " + String.join(", ", missing));
        }
    }

    private static void validateEvidence(EvaluationResult result, String groundedTranscript) {
        if (result.overallScore() > 30 && (result.evidence() == null || result.evidence().isEmpty())) {
            throw new EvaluationFormatException("evidence is required for a score above 30");
        }
        if (result.evidence() == null) return;
        for (EvaluationResult.Evidence evidence : result.evidence()) {
            if (evidence == null) throw new EvaluationFormatException("evidence entries must not be null");
            if (evidence.dimension() == null || evidence.dimension().isBlank()) {
                throw new EvaluationFormatException("evidence.dimension is required");
            }
            if (evidence.quote() == null || evidence.quote().isBlank()) {
                throw new EvaluationFormatException("evidence.quote is required");
            }
            if ((evidence.startSeconds() == null) != (evidence.endSeconds() == null)) {
                throw new EvaluationFormatException(
                        "evidence timestamps must both be present or both be null");
            }
            if (evidence.startSeconds() != null && (evidence.startSeconds() < 0
                    || evidence.endSeconds() < evidence.startSeconds())) {
                throw new EvaluationFormatException("evidence time range is invalid");
            }
            if (groundedTranscript != null
                    && !compact(groundedTranscript).contains(compact(evidence.quote()))) {
                throw new EvaluationFormatException("evidence.quote is not present in the transcript");
            }
        }
    }

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    private <T> T deserialize(String raw, Class<T> type) {
        if (raw == null || raw.isBlank()) {
            throw new EvaluationFormatException("Model returned an empty evaluation");
        }
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
        if (value != null && value < 0) {
            throw new EvaluationFormatException(field + " must not be negative");
        }
    }

    private static void nonNegative(Double value, String field) {
        if (value != null && (Double.isNaN(value) || value < 0)) {
            throw new EvaluationFormatException(field + " must not be negative");
        }
    }
}
