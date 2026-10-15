package com.guangge.Interview.resume.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 正文切块规则：识别错顶多影响字号，正文一个字都不能丢。 */
class ResumeLayoutTest {

    private static final String RESUME = """
            张伟
            电话：138-0000-0000 | 邮箱：zhangwei@example.com

            教育背景
            2016.09 - 2020.06  某某大学  计算机科学与技术  本科

            工作经历：某科技有限公司 / 后端开发工程师 / 2020.07-2024.03
            - 负责订单系统重构，接口数量减少 40%
            - 参与【请补充：日均单量】的峰值优化

            ## 项目经验
            主导订单中心拆分，QPS 从【请补充：QPS 数值】提升到 3 倍。
            """;

    @Test
    @DisplayName("小节名、Markdown 标题、列表项都能识别出来")
    void parsesHeadingsAndBullets() {
        List<ResumeLayout.Block> blocks = ResumeLayout.parse(RESUME);

        assertThat(blocks).anySatisfy(block -> {
            assertThat(block.kind()).isEqualTo(ResumeLayout.Kind.HEADING);
            assertThat(block.text()).isEqualTo("教育背景");
            assertThat(block.level()).isEqualTo(2);
        });
        assertThat(blocks).anySatisfy(block -> {
            assertThat(block.kind()).isEqualTo(ResumeLayout.Kind.HEADING);
            assertThat(block.text()).isEqualTo("项目经验");
            assertThat(block.level()).isEqualTo(3);
        });
        assertThat(blocks.stream().filter(block -> block.kind() == ResumeLayout.Kind.BULLET))
                .extracting(ResumeLayout.Block::text)
                .containsExactly(
                        "负责订单系统重构，接口数量减少 40%",
                        "参与【请补充：日均单量】的峰值优化");
    }

    @Test
    @DisplayName("「工作经历：」这种带冒号的短行也算小节，但因为太长不算")
    void treatsShortColonLineAsHeadingOnlyWhenShort() {
        List<ResumeLayout.Block> blocks = ResumeLayout.parse("联系方式：\n工作经历：某科技有限公司 / 后端开发工程师 / 2020.07-2024.03\n");

        assertThat(blocks.get(0).kind()).isEqualTo(ResumeLayout.Kind.HEADING);
        assertThat(blocks.get(0).text()).isEqualTo("联系方式");
        assertThat(blocks.get(1).kind()).isEqualTo(ResumeLayout.Kind.PARAGRAPH);
    }

    @Test
    @DisplayName("空行不产生空块，正文不丢字")
    void keepsEveryCharacter() {
        List<ResumeLayout.Block> blocks = ResumeLayout.parse(RESUME);

        assertThat(blocks).noneSatisfy(block -> assertThat(block.text()).isBlank());
        assertThat(blocks).extracting(ResumeLayout.Block::text).anySatisfy(text ->
                assertThat(text).contains("zhangwei@example.com"));
    }

    @Test
    @DisplayName("占位符单独切出来，前后正文不粘连")
    void splitsPlaceholders() {
        List<ResumeLayout.Piece> pieces = ResumeLayout.pieces("参与【请补充：日均单量】的峰值优化");

        assertThat(pieces).containsExactly(
                new ResumeLayout.Piece("参与", false),
                new ResumeLayout.Piece("【请补充：日均单量】", true),
                new ResumeLayout.Piece("的峰值优化", false));
    }

    @Test
    @DisplayName("导出 Markdown 时标题与小节层级都在")
    void rendersMarkdown() {
        String markdown = ResumeLayout.toMarkdown("张伟的简历", RESUME);

        assertThat(markdown).startsWith("# 张伟的简历\n");
        assertThat(markdown).contains("## 教育背景\n");
        assertThat(markdown).contains("### 项目经验\n");
        assertThat(markdown).contains("- 负责订单系统重构，接口数量减少 40%");
    }
}
