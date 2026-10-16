package com.guangge.Interview.resume.export;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDF 导出。
 *
 * 这里依赖机器上有中文字体（本机 Windows 有 msyh/simhei），
 * 在没字体的环境上跳过：那种环境本来就该用 resume.export.pdf-font 指定字体。
 */
class ResumePdfExporterTest {

    private final ResumePdfExporter exporter = new ResumePdfExporter("", "");

    @Test
    @DisplayName("生成的是 %PDF 文件，且中文正文能抽回来")
    void writesPdfWithChineseText() throws Exception {
        byte[] bytes = exporter.render("张伟的简历", """
                教育背景
                2016.09 - 2020.06  某某大学  计算机科学与技术  本科

                参与【请补充：日均单量】的峰值优化
                """);

        assertThat(bytes).isNotEmpty();
        assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        try (PDDocument document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", "");
            assertThat(text).contains("张伟的简历");
            assertThat(text).contains("教育背景");
            assertThat(text).contains("【请补充：日均单量】");
        }
    }

    @Test
    @DisplayName("长简历自动翻页")
    void paginatesLongResumes() throws Exception {
        String paragraph = "负责订单系统的重构与性能优化，接口数量减少 40%，并沉淀了配套的监控看板。\n";
        byte[] bytes = exporter.render("长简历", paragraph.repeat(80));

        try (PDDocument document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
        }
    }

    @Test
    @DisplayName("单页简历：文字都落在页边距内，不越界也不压到底部")
    void keepsTextInsideTheMargins() throws Exception {
        byte[] bytes = exporter.render("边界检查", """
                求职目标：Java 后端工程师

                基本信息
                - 姓名：张三
                - 手机：13800000000

                工作经历
                某某科技
                - 时间：2020.07 - 至今
                - 职责：负责订单系统的重构与性能优化，接口数量减少 40%，并沉淀了配套的监控看板。
                """);

        try (PDDocument document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            // A4：595.28 x 841.89，左右各 56pt 边距，上下 62/58pt
            for (float[] point : glyphPositions(document)) {
                assertThat(point[0]).as("文字左边不越过左页边距").isGreaterThanOrEqualTo(52f);
                assertThat(point[0]).as("文字右边不越过右页边距").isLessThanOrEqualTo(540f);
                assertThat(point[1]).as("文字不越过上页边距").isGreaterThanOrEqualTo(40f);
                assertThat(point[1]).as("文字不越过下页边距").isLessThanOrEqualTo(785f);
            }
        }
    }

    @Test
    @DisplayName("多页简历：页脚在底部，正文也不会压到它")
    void putsFootersAtTheBottomOfEveryPage() throws Exception {
        String paragraph = "负责订单系统的重构与性能优化，接口数量减少 40%，并沉淀了配套的监控看板，"
                + "同时把发布流程从手工改成自动化，回滚时间从 30 分钟降到 3 分钟。\n";
        byte[] bytes = exporter.render("分页检查", paragraph.repeat(30));

        try (PDDocument document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("第 1 / " + document.getNumberOfPages() + " 页");
            for (float[] point : glyphPositions(document)) {
                assertThat(point[1]).as("任何文字都不跑到页面下边缘外").isLessThanOrEqualTo(812f);
            }
        }
    }

    /** 每个字形的 x / y（y 从页顶往下算）。 */
    private static List<float[]> glyphPositions(PDDocument document) throws IOException {
        List<float[]> points = new ArrayList<>();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> textPositions) {
                for (TextPosition position : textPositions) {
                    points.add(new float[] {position.getXDirAdj(), position.getYDirAdj()});
                }
            }
        };
        stripper.getText(document);
        return points;
    }

    @Test
    @DisplayName("字体里没有的字符（emoji）被替换掉，不会让导出直接失败")
    void replacesUnsupportedGlyphs() throws Exception {
        byte[] bytes = exporter.render("简历", "爱好：跑步 🏃 与摄影 📷");

        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", "");
            assertThat(text).contains("爱好：跑步");
            assertThat(text).doesNotContain("🏃");
        }
    }

    @Test
    @DisplayName("一个候选字体都没有时，报错要指出怎么修")
    void failsWithActionableMessageWhenNoFont() throws Exception {
        try (PDDocument document = new PDDocument()) {
            assertThatThrownBy(() -> ResumePdfFonts.load(document, List.of(), List.of()))
                    .isInstanceOf(ResumeExportException.class)
                    .hasMessageContaining("resume.export.pdf-font");
        }
    }
}
