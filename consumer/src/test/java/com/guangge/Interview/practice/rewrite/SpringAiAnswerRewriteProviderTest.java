package com.guangge.Interview.practice.rewrite;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiAnswerRewriteProviderTest {

    @Test
    void forbidsAllDigitsWhenTheTranscriptHasNoNumbers() {
        assertThat(SpringAiAnswerRewriteProvider.numericConstraint("我只是解释了一下 STAR 结构。"))
                .contains("不得出现任何阿拉伯数字或中文数字")
                .contains("百分比")
                .doesNotContain("只允许");
    }

    @Test
    void listsTheOnlyNumbersTheModelMayReuse() {
        String constraint = SpringAiAnswerRewriteProvider.numericConstraint(
                "我把接口从 900ms 优化到 100ms，减少了 2 次调用。");

        assertThat(constraint).contains("900ms", "100ms", "2次");
        assertThat(constraint).contains("不得用阿拉伯数字、中文数字");
    }
}
