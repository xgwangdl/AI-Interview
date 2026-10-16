package com.guangge.Interview.practice.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 短板复练的口径：维度 key 在后端翻成中文，前端只传 key。 */
class SpringAiQuestionPlanProviderTest {

    @Test
    @DisplayName("已知维度翻成中文并说明出题倾向")
    void turnsKeysIntoInstructions() {
        String line = SpringAiQuestionPlanProvider.focusLine(List.of("starStructure", "logic"));

        assertThat(line).contains("STAR 结构").contains("回答逻辑")
                .contains("多出不答好就会暴露这些问题的题");
    }

    @Test
    @DisplayName("没有短板时不加任何提示（不改变原有出题行为）")
    void staysSilentWithoutFocus() {
        assertThat(SpringAiQuestionPlanProvider.focusLine(null)).isEmpty();
        assertThat(SpringAiQuestionPlanProvider.focusLine(List.of())).isEmpty();
    }

    @Test
    @DisplayName("不认识的维度直接忽略，而不是把 key 塞进提示词")
    void ignoresUnknownDimensions() {
        assertThat(SpringAiQuestionPlanProvider.focusLine(List.of("颜值", ""))).isEmpty();
        assertThat(SpringAiQuestionPlanProvider.focusLine(Arrays.asList((String) null))).isEmpty();

        String mixed = SpringAiQuestionPlanProvider.focusLine(List.of("颜值", "relevance"));
        assertThat(mixed).contains("内容相关性").doesNotContain("颜值");
    }
}
