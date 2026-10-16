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
    @DisplayName("挑字体：不存在的路径、空值直接跳过，一个都没有就是空")
    void picksTheFirstUsableCandidate() {
        assertThat(ResumePdfFonts.firstEmbeddable(List.of())).isEmpty();
        assertThat(ResumePdfFonts.firstEmbeddable(List.of("", "/does/not/exist.ttf"))).isEmpty();
        assertThat(ResumePdfFonts.firstEmbeddable(java.util.Arrays.asList((String) null))).isEmpty();
    }

    @Test
    @DisplayName("报错要说清 CFF 字体（fonts-noto-cjk）不能用以及怎么办")
    void errorMessageNamesTheOffendingFontFlavour() throws Exception {
        try (PDDocument document = new PDDocument()) {
            assertThatThrownBy(() -> ResumePdfFonts.load(document, List.of(), List.of()))
                    .isInstanceOf(ResumeExportException.class)
                    .hasMessageContaining("CFF")
                    .hasMessageContaining("fonts-wqy-zenhei");
        }
    }

    @Test
    @DisplayName("文件存在但不是可用字体（同 CFF 那条分支）也要跳过，而不是选中它")
    void skipsCandidatesThatCannotBeEmbedded() {
        // 用仓库根目录的 pom.xml 当"存在但用不了"的替身：
        // 走的是和 CFF 字体同一条分支 —— 能读到字节，但排不出来。
        assertThat(ResumePdfFonts.firstEmbeddable(List.of("../pom.xml"))).isEmpty();
        assertThat(ResumePdfFonts.firstEmbeddable(List.of("../pom.xml", ""))).isEmpty();
    }
}
