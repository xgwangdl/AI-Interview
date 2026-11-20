package com.guangge.Interview.resume.export;

import com.guangge.Interview.resume.ResumeDataFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeTextRendererTest {

    private final ResumeTextRenderer renderer = new ResumeTextRenderer();

    @Test
    void rendersChineseSectionsAndEvidenceWithoutHeuristicParsing() {
        String text = renderer.render(ResumeDataFixtures.sample("zh", "resume"));

        assertThat(text)
                .contains("张伟")
                .contains("职业摘要")
                .contains("核心能力")
                .contains("工作经历")
                .contains("- 负责订单系统重构，接口耗时从 800ms 降至 120ms。")
                .contains("- 合并重复查询并增加联合索引。");
    }

    @Test
    void localizesEnglishAndJapaneseDocuments() {
        assertThat(renderer.render(ResumeDataFixtures.sample("en", "resume")))
                .contains("PROFILE")
                .contains("CORE SKILLS")
                .contains("EXPERIENCE");
        assertThat(renderer.render(ResumeDataFixtures.sample("ja", "career_history")))
                .contains("職務要約")
                .contains("活かせる経験・スキル")
                .contains("職務経歴");
    }
}
