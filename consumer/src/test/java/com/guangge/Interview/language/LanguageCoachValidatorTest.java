package com.guangge.Interview.language;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 反馈校验：挡住两种会直接误导用户的情况 —— 编造原文、凑数。
 */
class LanguageCoachValidatorTest {

    private final LanguageCoachValidator validator = new LanguageCoachValidator(new ObjectMapper());
    private final String transcript = "I lead a migration last year and cut latency to 100ms.";

    @Test
    @DisplayName("正常返回：回复 + 最多两条建议，原文逐字出现在转写里才留下")
    void keepsOnlyGroundedFeedback() {
        LanguageCoachResult result = validator.parseTurn("""
                {"reply": "Nice. What was the hardest part of that migration?",
                 "feedback": [
                   {"original": "I lead a migration", "better": "I led a migration", "why": "过去式应为 led"},
                   {"original": "cut latency to 100ms", "better": "brought latency down to 100ms", "why": "更自然"},
                   {"original": "我根本没说过这句", "better": "x", "why": "编造的原文"}
                 ]}
                """, transcript);

        assertThat(result.reply()).contains("hardest part");
        // 第三条是模型编的原文：直接丢掉，而不是报错 —— 少一条错的建议比多一条好
        assertThat(result.feedback()).hasSize(2);
        assertThat(result.feedback().get(0).original()).isEqualTo("I lead a migration");
        assertThat(result.feedback().get(1).why()).isEqualTo("更自然");
    }

    @Test
    @DisplayName("大小写与空白差异不算编造原文")
    void ignoresCaseAndSpacingWhenMatching() {
        LanguageCoachResult result = validator.parseTurn("""
                {"reply": "Good.", "feedback": [
                  {"original": "I LEAD   a migration", "better": "I led a migration", "why": "时态"}
                ]}
                """, transcript);

        assertThat(result.feedback()).hasSize(1);
    }

    @Test
    @DisplayName("没有回复 / 不是 JSON / 空串都要报错，由上层转成可读的 502")
    void rejectsBrokenModelOutput() {
        assertThatThrownBy(() -> validator.parseTurn("{}", transcript))
                .isInstanceOf(LanguageCoachException.class);
        assertThatThrownBy(() -> validator.parseTurn("not json", transcript))
                .isInstanceOf(LanguageCoachException.class);
        assertThatThrownBy(() -> validator.parseTurn("   ", transcript))
                .isInstanceOf(LanguageCoachException.class);
    }

    @Test
    @DisplayName("报告：分数夹到 0-100，建议最多 3 条")
    void clampsReportScores() {
        LanguageReportResult report = validator.parseReport("""
                {"fluency": 130, "vocabulary": -5, "grammar": 70, "task": 80,
                 "summary": "整体清楚。", "improvements": ["a", "b", "c", "d"]}
                """);

        assertThat(report.fluency()).isEqualTo(100);
        assertThat(report.vocabulary()).isZero();
        assertThat(report.grammar()).isEqualTo(70);
        assertThat(report.improvements()).hasSize(3);
        assertThat(report.summary()).isEqualTo("整体清楚。");
    }
}
