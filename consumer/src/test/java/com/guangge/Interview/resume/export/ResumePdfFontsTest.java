package com.guangge.Interview.resume.export;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 字体找不到时的行为：宁可明确报错，也不生成一份乱码 PDF。这条不依赖机器上有中文字体。 */
class ResumePdfFontsTest {

    @Test
    @DisplayName("一个候选字体都没有时，报错要指出怎么修")
    void failsWithActionableMessageWhenNoFont() throws Exception {
        try (PDDocument document = new PDDocument()) {
            assertThatThrownBy(() -> ResumePdfFonts.load(document, List.of(), List.of()))
                    .isInstanceOf(ResumeExportException.class)
                    .hasMessageContaining("resume.export.pdf-font");
        }
    }

    @Test
    @DisplayName("候选列表是否可用：只看文件在不在")
    void reportsWhetherAnyCandidateExists() {
        assertThat(ResumePdfFonts.anyAvailable(List.of())).isFalse();
        assertThat(ResumePdfFonts.anyAvailable(List.of("", "/does/not/exist.ttf"))).isFalse();
        assertThat(ResumePdfFonts.anyAvailable(java.util.Arrays.asList((String) null))).isFalse();
    }
}
