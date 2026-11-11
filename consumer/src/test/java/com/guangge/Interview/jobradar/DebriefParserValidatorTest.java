package com.guangge.Interview.jobradar;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DebriefParserValidatorTest {
    private final DebriefParserValidator validator = new DebriefParserValidator();

    @Test
    void keepsQuestionsWhoseKeywordsAppearInTheOriginalText() {
        String raw = "先问项目经历，然后深挖缓存一致性，最后手写 LRU。";
        DebriefParseResult parsed = new DebriefParseResult(List.of(
                question("项目经历里最难的挑战", "project_depth", true),
                question("缓存一致性怎么保证", "system_design", true)), List.of(), "summary");

        var result = validator.validate(raw, parsed);

        assertThat(result.questions()).hasSize(2);
        assertThat(result.dropped()).isZero();
    }

    @Test
    void dropsHallucinatedQuestion() {
        String raw = "只问了项目经历。";
        DebriefParseResult parsed = new DebriefParseResult(List.of(
                question("项目经历介绍", "project_depth", false),
                question("Kubernetes 调度器怎么实现", "system_design", true)), List.of(), "summary");

        var result = validator.validate(raw, parsed);

        assertThat(result.questions()).extracting(DebriefParseResult.DebriefQuestion::text)
                .containsExactly("项目经历介绍");
        assertThat(result.dropped()).isEqualTo(1);
    }

    @Test
    void dropsDuplicateAndUnknownCategory() {
        String raw = "问了缓存一致性，也问了缓存一致性。";
        DebriefParseResult parsed = new DebriefParseResult(List.of(
                question("缓存一致性怎么保证", "system_design", true),
                question("缓存一致性怎么保证", "system_design", true),
                question("缓存一致性细节", "unknown_category", false)), List.of(), "summary");

        var result = validator.validate(raw, parsed);

        assertThat(result.questions()).hasSize(1);
        assertThat(result.dropped()).isEqualTo(2);
    }

    private static DebriefParseResult.DebriefQuestion question(String text, String category, boolean hard) {
        return new DebriefParseResult.DebriefQuestion(text, category, hard, null);
    }
}
