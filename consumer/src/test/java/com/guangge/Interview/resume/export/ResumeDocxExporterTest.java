package com.guangge.Interview.resume.export;

import com.guangge.Interview.resume.ResumeDataFixtures;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Word 导出：能被 POI 读回来，说明是合法的 .docx，且正文齐全。 */
class ResumeDocxExporterTest {

    private final ResumeDocxExporter exporter = new ResumeDocxExporter();

    @Test
    @DisplayName("生成的 docx 可以重新打开，标题、小节与正文都在")
    void writesReadableDocx() throws Exception {
        byte[] bytes = exporter.render("张伟的简历", """
                教育背景
                2016.09 - 2020.06  某某大学  计算机科学与技术  本科

                参与【请补充：日均单量】的峰值优化
                """);

        assertThat(bytes).isNotEmpty();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            assertThat(document.getParagraphs()).extracting(XWPFParagraph::getText)
                    .contains("张伟的简历", "教育背景", "参与【请补充：日均单量】的峰值优化");
        }
    }

    @Test
    @DisplayName("docx 是 zip 包（PK 头），不是纯文本冒充")
    void writesZipContainer() throws Exception {
        byte[] bytes = exporter.render("简历", "工作经历\n做了些事情。");

        assertThat(new String(bytes, 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");
    }

    @Test
    @DisplayName("空标题、空正文不抛异常")
    void toleratesEmptyInput() throws Exception {
        byte[] bytes = exporter.render(null, "");

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            assertThat(document.getParagraphs()).extracting(XWPFParagraph::getText).contains("简历");
        }
    }

    @Test
    @DisplayName("结构化简历直接按 schema 渲染，不经纯文本猜段落")
    void rendersStructuredResumeData() throws Exception {
        byte[] bytes = exporter.render(ResumeDataFixtures.sample("zh", "resume"), ResumeTheme.GREEN);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            assertThat(document.getParagraphs()).extracting(XWPFParagraph::getText)
                    .contains("张伟", "职业摘要", "核心能力", "工作经历", "项目经历", "教育背景")
                    .contains("某某科技　·　Java 后端工程师")
                    .contains("2020.07 - 至今")
                    .contains("• 负责订单系统重构，接口耗时从 800ms 降至 120ms。");
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "resume.visual.qa", matches = "true")
    @DisplayName("生成一份结构化 DOCX 供文档技能做逐页视觉检查")
    void writesVisualQaFixture() throws Exception {
        Path output = Path.of("target", "resume-visual", "structured-resume.docx");
        Files.createDirectories(output.getParent());
        Files.write(output, exporter.render(
                ResumeDataFixtures.sample("zh", "resume"), ResumeTheme.GREEN));
        assertThat(output).exists();
    }
}
