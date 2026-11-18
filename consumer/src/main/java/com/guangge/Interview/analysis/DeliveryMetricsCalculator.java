package com.guangge.Interview.analysis;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Computes delivery metrics without asking the model to infer timing it cannot see.
 * Pause metrics stay unknown until ASR provides sentence-level timestamps.
 */
@Component
public class DeliveryMetricsCalculator {

    private static final List<Pattern> FILLER_PATTERNS = List.of(
            Pattern.compile("嗯+"),
            Pattern.compile("呃+"),
            Pattern.compile("额+"),
            Pattern.compile("\\bum+\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\buh+\\b", Pattern.CASE_INSENSITIVE));

    public EvaluationResult.DeliveryMetrics calculate(String transcript, Integer durationSeconds) {
        String text = transcript == null ? "" : transcript;
        Double speechRate = null;
        if (durationSeconds != null && durationSeconds > 0) {
            int characters = spokenCharacterCount(text);
            speechRate = round(characters * 60.0 / durationSeconds);
        }
        return new EvaluationResult.DeliveryMetrics(
                speechRate,
                null,
                null,
                fillerWordCount(text));
    }

    private static int spokenCharacterCount(String text) {
        int count = 0;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (Character.isLetterOrDigit(codePoint)) count++;
            offset += Character.charCount(codePoint);
        }
        return count;
    }

    private static int fillerWordCount(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        int count = 0;
        for (Pattern pattern : FILLER_PATTERNS) {
            Matcher matcher = pattern.matcher(normalized);
            while (matcher.find()) count++;
        }
        return count;
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
