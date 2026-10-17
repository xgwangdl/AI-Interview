package com.guangge.Interview.language;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.util.JsonText;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 校验模型的输出。
 *
 * 两件必须挡住的事（都是会直接误导用户的那种）：
 * 1. feedback 的 {@code original} 不是用户原话 —— 用户会照着一条自己没说过的话去"改错"；
 * 2. 建议凑数（每轮都硬挑两条），或者 reply 变成一大段 —— 前者让反馈失去意义，后者毁掉对话节奏。
 */
@Component
public class LanguageCoachValidator {

    private static final int MAX_REPLY_CHARS = 400;
    private static final int MAX_FEEDBACK = 2;
    private static final int MAX_FRAGMENT_CHARS = 200;

    private final ObjectMapper objectMapper;

    public LanguageCoachValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LanguageCoachResult parseTurn(String raw, String transcript) {
        JsonNode root = json(raw);
        String reply = text(root, "reply");
        if (reply == null) throw new LanguageCoachException("模型没有返回回复内容");
        List<LanguageFeedback> feedback = new ArrayList<>();
        for (JsonNode item : root.withArray("feedback")) {
            if (feedback.size() >= MAX_FEEDBACK) break;
            LanguageFeedback parsed = toFeedback(item, transcript);
            if (parsed != null) feedback.add(parsed);
        }
        return new LanguageCoachResult(truncate(reply, MAX_REPLY_CHARS), List.copyOf(feedback));
    }

    public LanguageReportResult parseReport(String raw) {
        JsonNode root = json(raw);
        List<String> improvements = new ArrayList<>();
        for (JsonNode item : root.withArray("improvements")) {
            String value = item.asText("").strip();
            if (!value.isEmpty() && improvements.size() < 3) improvements.add(truncate(value, 300));
        }
        String summary = text(root, "summary");
        return new LanguageReportResult(
                score(root, "fluency"),
                score(root, "vocabulary"),
                score(root, "grammar"),
                score(root, "task"),
                summary == null ? "本次练习已完成。" : truncate(summary, 400),
                List.copyOf(improvements));
    }

    /** L2 的反馈只返回数组，校验规则与 L1 一致（逐字核对 original）。 */
    public List<LanguageFeedback> parseFeedback(String raw, String transcript) {
        JsonNode root = json(raw);
        List<LanguageFeedback> feedback = new ArrayList<>();
        for (JsonNode item : root.withArray("feedback")) {
            if (feedback.size() >= MAX_FEEDBACK) break;
            LanguageFeedback parsed = toFeedback(item, transcript);
            if (parsed != null) feedback.add(parsed);
        }
        return List.copyOf(feedback);
    }

    /** original 必须真的出现在转写里；空字段/编造的片段直接丢掉，而不是报错 —— 少一条建议比错一条好。 */
    private static LanguageFeedback toFeedback(JsonNode item, String transcript) {
        String original = text(item, "original");
        String better = text(item, "better");
        String why = text(item, "why");
        if (original == null || better == null) return null;
        if (!normalize(transcript).contains(normalize(original))) return null;
        return new LanguageFeedback(truncate(original, MAX_FRAGMENT_CHARS), truncate(better, MAX_FRAGMENT_CHARS),
                why == null ? "" : truncate(why, MAX_FRAGMENT_CHARS));
    }

    private JsonNode json(String raw) {
        if (raw == null || raw.isBlank()) throw new LanguageCoachException("模型返回为空");
        try {
            return objectMapper.readTree(JsonText.stripCodeFence(raw));
        } catch (Exception exception) {
            throw new LanguageCoachException("模型返回的不是约定的 JSON：" + exception.getMessage(), exception);
        }
    }

    private static Integer score(JsonNode root, String field) {
        if (!root.hasNonNull(field)) return null;
        int value = root.get(field).asInt();
        return Math.max(0, Math.min(100, value));
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) return null;
        String value = node.get(field).asText("").strip();
        return value.isEmpty() ? null : value;
    }

    /** 比对前统一空白与大小写：模型偶尔会把空格或大小写改掉，那不算"编造原文"。 */
    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
