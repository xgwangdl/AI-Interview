package com.guangge.Interview.jobradar;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DebriefParserValidator {
    private static final Pattern ALPHANUMERIC = Pattern.compile("[A-Za-z0-9]{2,}");
    private static final Pattern HAN_RUN = Pattern.compile("\\p{IsHan}+");
    private static final Set<String> CATEGORIES = Set.of("project_depth", "system_design", "algorithm",
            "behavioral", "domain", "other");

    public ValidationResult validate(String rawText, DebriefParseResult parsed) {
        if (parsed == null) return new ValidationResult(List.of(), 0);
        String source = normalize(rawText);
        Set<String> seen = new HashSet<>();
        List<DebriefParseResult.DebriefQuestion> accepted = new ArrayList<>();
        int dropped = 0;
        for (DebriefParseResult.DebriefQuestion question : parsed.questions()) {
            if (accepted.size() >= 10) break;
            String text = question == null || question.text() == null ? "" : question.text().strip();
            String category = question == null || question.category() == null ? "other" : question.category().strip();
            String key = text.toLowerCase();
            if (text.length() < 5 || text.length() > 300 || !CATEGORIES.contains(category)
                    || !seen.add(key) || !appearsInSource(source, text)) {
                dropped++;
                continue;
            }
            accepted.add(new DebriefParseResult.DebriefQuestion(text, category, question.hard(), question.note()));
        }
        return new ValidationResult(List.copyOf(accepted), dropped);
    }

    private static boolean appearsInSource(String source, String question) {
        String normalizedQuestion = normalize(question);
        Matcher latinToken = ALPHANUMERIC.matcher(normalizedQuestion);
        while (latinToken.find()) {
            if (latinToken.group().length() >= 3 && source.contains(latinToken.group())) return true;
        }
        Set<String> sourceBigrams = hanBigrams(source);
        int overlap = 0;
        for (String bigram : hanBigrams(normalizedQuestion)) {
            if (sourceBigrams.contains(bigram)) overlap++;
        }
        // Require more than one shared pair so a generic two-character word cannot
        // make a hallucinated question look grounded in the user's notes.
        return overlap >= 2;
    }

    private static Set<String> hanBigrams(String value) {
        Set<String> result = new HashSet<>();
        Matcher matcher = HAN_RUN.matcher(value);
        while (matcher.find()) {
            String run = matcher.group();
            for (int i = 0; i + 1 < run.length(); i++) {
                result.add(run.substring(i, i + 2));
            }
        }
        return result;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        StringBuilder normalized = new StringBuilder(value.length());
        value.toLowerCase().codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(normalized::appendCodePoint);
        return normalized.toString();
    }

    public record ValidationResult(List<DebriefParseResult.DebriefQuestion> questions, int dropped) { }
}
