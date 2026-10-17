package com.guangge.Interview.practice.rewrite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 改写不能编造数字；用户自己编辑允许补充真实信息。 */
class AnswerRewriteValidatorTest {

    private final AnswerRewriteValidator validator = new AnswerRewriteValidator();

    @Test
    @DisplayName("AI 改写不能引入原回答没有的数字")
    void blocksInventedMetrics() {
        assertThat(validator.validate("我把接口耗时从 900ms 降到 100ms。", "接口从 900ms 优化到 100ms"))
                .contains("900ms");
        assertThatThrownBy(() -> validator.validate("我把性能提升了 40%。", "我优化了接口性能。"))
                .isInstanceOf(AnswerRewriteException.class)
                .hasMessageContaining("40");
    }

    @Test
    @DisplayName("用户编辑可以补真实数字（例如语音里没说出来的数据）")
    void allowsUserSuppliedNumbers() {
        assertThat(validator.validateUser("我在 2024 年主导了这个项目。"))
                .contains("2024");
    }

    @Test
    @DisplayName("空改写直接拒绝")
    void rejectsEmpty() {
        assertThatThrownBy(() -> validator.validate(" ", "原文"))
                .isInstanceOf(AnswerRewriteException.class);
    }

    @Test
    @DisplayName("英文回答不能被模型翻译成中文")
    void preservesTheAnswerLanguage() {
        assertThatThrownBy(() -> validator.validate(
                "我主导了计费系统迁移，把延迟从 900ms 降到 100ms。",
                "I led the billing migration and cut latency from 900ms to 100ms."))
                .isInstanceOf(AnswerRewriteException.class)
                .hasMessageContaining("英文");
    }
}
