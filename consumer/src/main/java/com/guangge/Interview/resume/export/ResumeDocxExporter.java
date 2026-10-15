package com.guangge.Interview.resume.export;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;

/**
 * 简历导出 Word（.docx）。
 *
 * 用 Apache POI 直接生成 OOXML，不经过 HTML 中转：字体、字号、缩进都是原生属性，
 * 打开后能继续改（这是 PDF 给不了的），也没有 iText 那套 AGPL 授权问题。
 */
@Component
public class ResumeDocxExporter {

    /** 中文字体名要写中文字面量，Word 才认（eastAsia 字符集）。 */
    private static final String CJK_FONT = "微软雅黑";
    private static final String LATIN_FONT = "Calibri";
    /** 待补充占位符的颜色（琥珀），和网页端高亮一致。 */
    private static final String PLACEHOLDER_COLOR = "B45309";

    /** OOXML 的字号是半磅：18pt = 36。 */
    private static final int TITLE_HALF_POINTS = 36;
    private static final int HEADING_HALF_POINTS = 26;
    private static final int SUB_HEADING_HALF_POINTS = 23;
    private static final int BODY_HALF_POINTS = 21;
    private static final int BULLET_INDENT_TWIPS = 284;
    /** 悬挂缩进：项目符号挂在左侧，折行文字与第一行文字对齐。 */
    private static final int BULLET_HANGING_TWIPS = 284;
    private static final int PAGE_MARGIN_TWIPS = 1134;

    public byte[] render(String title, String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            pageSetup(document);
            writeParagraph(document, title == null || title.isBlank() ? "简历" : title.strip(),
                    ParagraphAlignment.CENTER, TITLE_HALF_POINTS, true, 0, 220, false);
            for (ResumeLayout.Block block : ResumeLayout.parse(text, title)) {
                switch (block.kind()) {
                    case HEADING -> writeParagraph(document, block.text(), ParagraphAlignment.LEFT,
                            block.level() <= 2 ? HEADING_HALF_POINTS : SUB_HEADING_HALF_POINTS,
                            true, 0, 200, block.level() <= 2);
                    case BULLET -> writeParagraph(document, "• " + block.text(), ParagraphAlignment.LEFT,
                            BODY_HALF_POINTS, false, BULLET_INDENT_TWIPS, 60, false);
                    case PARAGRAPH -> writeParagraph(document, block.text(), ParagraphAlignment.LEFT,
                            BODY_HALF_POINTS, false, 0, 120, false);
                }
            }
            document.write(out);
            return out.toByteArray();
        }
    }

    private void writeParagraph(XWPFDocument document, String text, ParagraphAlignment alignment,
                                int halfPoints, boolean bold, int indentTwips, int spaceAfterTwips,
                                boolean sectionRule) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(alignment);
        // 小节标题下面走一条细线，和常见简历模板一致
        if (sectionRule) paragraph.setBorderBottom(Borders.SINGLE);
        if (indentTwips > 0) {
            paragraph.setIndentationLeft(indentTwips);
            paragraph.setIndentationHanging(BULLET_HANGING_TWIPS);
        }
        if (spaceAfterTwips > 0) paragraph.setSpacingAfter(spaceAfterTwips);
        for (ResumeLayout.Piece piece : ResumeLayout.pieces(text)) {
            XWPFRun run = paragraph.createRun();
            run.setText(piece.text());
            run.setBold(bold || piece.placeholder());
            run.setFontSize(halfPoints);
            run.setFontFamily(LATIN_FONT);
            run.setFontFamily(CJK_FONT, XWPFRun.FontCharRange.eastAsia);
            if (piece.placeholder()) run.setColor(PLACEHOLDER_COLOR);
        }
    }

    /** A4 竖版 + 2cm 页边距：收件人那边的 Word 版式基本一致。 */
    private void pageSetup(XWPFDocument document) {
        CTSectPr section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        CTPageSz size = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        size.setW(BigInteger.valueOf(11906));
        size.setH(BigInteger.valueOf(16838));
        CTPageMar margin = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        margin.setTop(BigInteger.valueOf(PAGE_MARGIN_TWIPS));
        margin.setBottom(BigInteger.valueOf(PAGE_MARGIN_TWIPS));
        margin.setLeft(BigInteger.valueOf(PAGE_MARGIN_TWIPS));
        margin.setRight(BigInteger.valueOf(PAGE_MARGIN_TWIPS));
    }
}
