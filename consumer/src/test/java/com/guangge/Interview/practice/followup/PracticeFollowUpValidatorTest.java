package com.guangge.Interview.practice.followup;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 追问的输出校验：null / 空串都算"不追问"，绝不能当成异常。 */
class PracticeFollowUpValidatorTest {

    private final PracticeFollowUpValidator validator = new PracticeFollowUpValidator(new ObjectMapper());

    @Test
    @DisplayName("模型给出追问就照用，理由一起留下")
    void keepsFollowUp() {
        PracticeFollowUpResult result = validator.parse(
                "{\"followUp\": \"你说的优化具体改了什么？\", \"reason\": \"只给了结论没讲做法\"}");

        assertThat(result.followUp()).isEqualTo("你说的优化具体改了什么？");
        assertThat(result.reason()).isEqualTo("只给了结论没讲做法");
    }

    @Test
    @DisplayName("null / 空串 / \"none\" 都表示不追问，而不是格式错误")
    void treatsBlankAsNoFollowUp() {
        assertThat(validator.parse("{\"followUp\": null, \"reason\": \"回答很完整\"}").followUp()).isNull();
        assertThat(validator.parse("{\"followUp\": \"  \", \"reason\": \"\"}").followUp()).isNull();
        assertThat(validator.parse("{\"followUp\": \"none\", \"reason\": \"\"}").followUp()).isNull();
    }

    @Test
    @DisplayName("追问过长就截断，不因为啰嗦让整次答题报错")
    void truncatesLongFollowUp() {
        String longQuestion = "你".repeat(120);

        PracticeFollowUpResult result = validator.parse(
                "{\"followUp\": \"" + longQuestion + "\", \"reason\": \"\"}");

        assertThat(result.followUp()).hasSize(60);
    }

    @Test
    @DisplayName("代码块包着 JSON 也能救回来；真不是 JSON 才报错")
    void parsesFencedJson() {
        assertThat(validator.parse("```json\n{\"followUp\": \"展开说说\", \"reason\": \"\"}\n```").followUp())
                .isEqualTo("展开说说");
        assertThatThrownBy(() -> validator.parse("我不知道该问什么"))
                .isInstanceOf(PracticeFollowUpException.class);
        assertThatThrownBy(() -> validator.parse("  "))
                .isInstanceOf(PracticeFollowUpException.class);
    }
}
