package com.guangge.Interview.resume.export;

import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFieldRun;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;

/**
 * 简历导出 Word（.docx）。
 *
 * 版式和 PDF 那份对齐：顶部姓名 + 求职意向 + 强调色分隔线，小节标题带强调色与细线，
 * 条目用圆点、字段名加粗，页脚放页码。强调色跟着账号的主题色走（{@link ResumeTheme}）。
 *
 * 用 Apache POI 直接生成 OOXML，不经过 HTML 中转：字体、字号、缩进都是原生属性，
 * 打开后能继续改（这是 PDF 给不了的），也没有 iText 那套 AGPL 授权问题。
 */
@Component
public class ResumeDocxExporter {

    /** 中文字体名要写中文字面量，Word 才认（eastAsia 字符集）。 */
    private static final String CJK_FONT = "微软雅黑";
    private static final String LATIN_FONT = "Calibri";

    private static final String INK = "26303B";
    private static final String BODY = "3C4650";
    private static final String MUTED = "78838F";
    private static final String RULE = "DCE3E8";
    private static final String PLACEHOLDER_CHAR = "B45309";

    /** OOXML 的字号是半磅：18pt = 36。 */
    private static final int NAME_HALF_POINTS = 42;
    private static final int OBJECTIVE_HALF_POINTS = 21;
    private static final int HEADING_HALF_POINTS = 24;
    private static final int SUB_HEADING_HALF_POINTS = 22;
    private static final int ENTRY_HALF_POINTS = 22;
    private static final int BODY_HALF_POINTS = 20;

    private static final int BULLET_INDENT_TWIPS = 292;
    private static final int BULLET_HANGING_TWIPS = 292;
    private static final int PAGE_MARGIN_TWIPS = 1080;

    public byte[] render(String title, String text) throws IOException {
        return render(title, text, ResumeTheme.GREEN);
    }

    public byte[] render(String title, String text, ResumeTheme theme) throws IOException {
        String name = title == null || title.isBlank() ? "简历" : title.strip();
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            pageSetup(document);
            writeHeader(document, name, text, theme);
            writeFooter(document, name, theme);
            document.write(out);
            return out.toByteArray();
        }
    }

    private void writeHeader(XWPFDocument document, String name, String text, ResumeTheme theme) {
        writeParagraph(document, name, ParagraphAlignment.CENTER, NAME_HALF_POINTS, true, INK, 0, 40, null);
        java.util.List<ResumeLayout.Block> blocks = ResumeLayout.parse(text, name);

        // 紧跟姓名的"求职目标：xxx"放到顶栏当副标题，正文里就不再重复
        int start = 0;
        if (!blocks.isEmpty() && isObjective(blocks.get(0).text(), blocks.get(0).kind())) {
            writeParagraph(document, blocks.get(0).text(), ParagraphAlignment.CENTER, OBJECTIVE_HALF_POINTS,
                    false, theme.accentDark(), 0, 120, null);
            start = 1;
        }
        // 顶栏分隔线：用一个只有下边框的空段落画出来
        writeParagraph(document, "", ParagraphAlignment.CENTER, BODY_HALF_POINTS, false, INK,
                0, 240, border(theme.accent(), 12));

        for (int index = start; index < blocks.size(); index++) {
            ResumeLayout.Block block = blocks.get(index);
            switch (block.kind()) {
                case HEADING -> writeParagraph(document, block.text(), ParagraphAlignment.LEFT,
                        block.level() <= 2 ? HEADING_HALF_POINTS : SUB_HEADING_HALF_POINTS, true,
                        theme.accentDark(), 200, 120, border(RULE, 6));
                case BULLET -> writeBullet(document, block.text());
                case PARAGRAPH -> {
                    boolean entry = index == start || blocks.get(index - 1).kind() == ResumeLayout.Kind.HEADING;
                    writeParagraph(document, block.text(), ParagraphAlignment.LEFT,
                            entry ? ENTRY_HALF_POINTS : BODY_HALF_POINTS, entry,
                            entry ? INK : BODY, entry ? 140 : 60, 100, null);
                }
            }
        }
    }

    /** 条目：圆点 + 悬挂缩进，字段名（"时间："）加粗。 */
    private void writeBullet(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        paragraph.setIndentationLeft(BULLET_INDENT_TWIPS);
        paragraph.setIndentationHanging(BULLET_HANGING_TWIPS);
        paragraph.setSpacingAfter(60);

        run(paragraph, "● ", false, BODY_HALF_POINTS, INK);
        boolean labelDone = false;
        int colon = colonIndex(text);
        int offset = 0;
        for (ResumeLayout.Piece piece : ResumeLayout.pieces(text)) {
            String value = piece.text();
            int pieceStart = offset;
            offset += value.length();
            if (piece.placeholder()) {
                run(paragraph, value, true, BODY_HALF_POINTS, PLACEHOLDER_CHAR);
                labelDone = true;
                continue;
            }
            if (!labelDone && colon >= 0 && pieceStart + value.length() > colon) {
                int cut = colon + 1 - pieceStart;
                if (cut > 0) {
                    run(paragraph, value.substring(0, cut), true, BODY_HALF_POINTS, INK);
                    run(paragraph, value.substring(cut), false, BODY_HALF_POINTS, BODY);
                    labelDone = true;
                    continue;
                }
            }
            run(paragraph, value, false, BODY_HALF_POINTS, BODY);
        }
    }

    /** 紧跟姓名的那行如果是"求职目标：xxx"这种短句，就当顶栏副标题。 */
    private static boolean isObjective(String value, ResumeLayout.Kind kind) {
        if (kind != ResumeLayout.Kind.PARAGRAPH) return false;
        String text = value.strip();
        boolean looksLikeObjective = text.contains("求职") || text.contains("应聘") || text.startsWith("目标");
        return text.length() <= 40 && looksLikeObjective;
    }

    /** 只有开头几个字以内的冒号才算"字段名"分隔。 */
    private static int colonIndex(String text) {
        int colon = text.indexOf('：');
        if (colon < 0) colon = text.indexOf(':');
        return colon >= 0 && colon <= 7 ? colon : -1;
    }

    private void writeParagraph(XWPFDocument document, String text, ParagraphAlignment alignment,
                                int halfPoints, boolean bold, String color,
                                int spaceBeforeTwips, int spaceAfterTwips, BorderStyle border) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(alignment);
        if (spaceBeforeTwips > 0) paragraph.setSpacingBefore(spaceBeforeTwips);
        if (spaceAfterTwips > 0) paragraph.setSpacingAfter(spaceAfterTwips);
        if (border != null) {
            paragraph.setBorderBottom(Borders.SINGLE);
            CTPPr properties = paragraph.getCTP().getPPr();
            CTBorder bottom = properties.getPBdr().getBottom();
            bottom.setColor(border.color());
            bottom.setSz(BigInteger.valueOf(border.eighthPoints()));
            bottom.setSpace(BigInteger.valueOf(2));
        }
        if (text.isEmpty()) return;
        for (ResumeLayout.Piece piece : ResumeLayout.pieces(text)) {
            run(paragraph, piece.text(), bold || piece.placeholder(), halfPoints,
                    piece.placeholder() ? PLACEHOLDER_CHAR : color);
        }
    }

    private void run(XWPFParagraph paragraph, String text, boolean bold, int halfPoints, String color) {
        if (text.isEmpty()) return;
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setColor(color);
        run.setFontSize(halfPoints);
        run.setFontFamily(LATIN_FONT);
        run.setFontFamily(CJK_FONT, XWPFRun.FontCharRange.eastAsia);
    }

    /** 页脚：左侧姓名 + 右侧"第 X / Y 页"（用域，Word 打开会自己算）。 */
    private void writeFooter(XWPFDocument document, String name, ResumeTheme theme) {
        XWPFHeaderFooterPolicy policy = document.createHeaderFooterPolicy();
        XWPFFooter footer = policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT);
        XWPFParagraph paragraph = footer.getParagraphArray(0) != null
                ? footer.getParagraphArray(0) : footer.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        run(paragraph, name + "　·　第 ", false, 16, MUTED);
        XWPFFieldRun page = paragraph.createFieldRun();
        page.setFieldInstruction(" PAGE ");
        style(page, false, 16, MUTED);
        run(paragraph, " / ", false, 16, MUTED);
        XWPFFieldRun total = paragraph.createFieldRun();
        total.setFieldInstruction(" NUMPAGES ");
        style(total, false, 16, MUTED);
        run(paragraph, " 页", false, 16, MUTED);
    }

    private void style(XWPFRun run, boolean bold, int halfPoints, String color) {
        run.setBold(bold);
        run.setColor(color);
        run.setFontSize(halfPoints);
        run.setFontFamily(LATIN_FONT);
        run.setFontFamily(CJK_FONT, XWPFRun.FontCharRange.eastAsia);
    }

    /** A4 竖版 + 1.9cm 页边距：收件人那边的 Word 版式基本一致。 */
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

    /** 段落下边框：颜色 + 粗细（八分之一磅）。 */
    private record BorderStyle(String color, int eighthPoints) { }

    private static BorderStyle border(String color, int eighthPoints) {
        return new BorderStyle(color, eighthPoints);
    }
}
