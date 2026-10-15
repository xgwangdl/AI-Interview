package com.guangge.Interview.resume.export;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

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
}
