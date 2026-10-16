package com.guangge.Interview.resume.export;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 简历导出 PDF。
 *
 * 版式是一套简历模板，不是"把正文按行印出来"：顶部姓名 + 求职意向 + 强调色分隔线，
 * 小节标题带色块与细线，条目用矢量圆点、字段名（"时间："）加粗，多页时页脚有页码。
 * 强调色跟着账号的主题色走（见 {@link ResumeTheme}），和界面是同一套配色。
 *
 * 直接排版（PDFBox），不经过 HTML→PDF 中转：依赖只有一个，不牵涉 iText 的 AGPL，
 * 也不用把内容拼成 HTML 再交给一个渲染引擎猜版式。
 */
@Component
public class ResumePdfExporter {

    private static final Logger log = LoggerFactory.getLogger(ResumePdfExporter.class);

    private static final float MARGIN_LEFT = 54f;
    private static final float MARGIN_RIGHT = 54f;
    private static final float MARGIN_TOP = 56f;
    private static final float MARGIN_BOTTOM = 60f;
    private static final float FOOTER_RULE_Y = 52f;

    private static final float NAME_SIZE = 21f;
    private static final float OBJECTIVE_SIZE = 10.5f;
    private static final float SECTION_SIZE = 12f;
    private static final float SUB_SECTION_SIZE = 11f;
    private static final float ENTRY_SIZE = 10.8f;
    private static final float BODY_SIZE = 10f;
    private static final float FOOTER_SIZE = 8f;

    /** 行距系数：中文正文 1.55 倍读起来不挤，页面也不会被撑太长。 */
    private static final float LINE_FACTOR = 1.55f;
    private static final float BULLET_GAP = 13f;
    private static final float BULLET_RADIUS = 1.75f;
    private static final float SECTION_BAR_WIDTH = 3.2f;
    private static final float SECTION_BAR_HEIGHT = 11.5f;
    private static final float SECTION_GAP_BEFORE = 14f;
    private static final float SECTION_GAP_AFTER = 6.5f;
    private static final float ENTRY_GAP_BEFORE = 7f;
    private static final float ITEM_GAP = 2.5f;

    /** 固定墨色：中性色，配任何主题色都成立。 */
    private static final PDColor INK = rgb("26303B");
    private static final PDColor BODY = rgb("3C4650");
    private static final PDColor MUTED = rgb("78838F");
    private static final PDColor RULE = rgb("DCE3E8");
    private static final PDColor PLACEHOLDER = rgb("B45309");

    /** 候选字体：先找用户指定的，再按 Windows / macOS / Linux 的常见路径试。 */
    private static final List<String> DEFAULT_REGULAR_FONTS = List.of(
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/msyh.ttc",
            "C:/Windows/Fonts/msyhl.ttc",
            "C:/Windows/Fonts/simsun.ttc",
            "C:/Windows/Fonts/msjh.ttc",
            "/System/Library/Fonts/PingFang.ttc",
            "/System/Library/Fonts/Supplemental/Songti.ttc",
            "/Library/Fonts/Arial Unicode.ttf",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
            "/usr/share/fonts/truetype/arphic/uming.ttc");

    private static final List<String> DEFAULT_BOLD_FONTS = List.of(
            "C:/Windows/Fonts/msyhbd.ttc",
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/msjhbd.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Bold.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc");

    private final List<String> regularCandidates;
    private final List<String> boldCandidates;

    public ResumePdfExporter(@Value("${resume.export.pdf-font:}") String configuredRegular,
                             @Value("${resume.export.pdf-font-bold:}") String configuredBold) {
        this.regularCandidates = candidates(configuredRegular, DEFAULT_REGULAR_FONTS);
        this.boldCandidates = candidates(configuredBold, DEFAULT_BOLD_FONTS);
    }

    public byte[] render(String title, String text) throws IOException {
        return render(title, text, ResumeTheme.GREEN);
    }

    /**
     * 本机有没有可用于 PDF 的中文字体。
     *
     * 没有字体时导出会明确报错而不是生成乱码（见 {@link ResumePdfFonts}），
     * 这是产品行为；但"机器上没装字体"不是代码缺陷，所以测试据此决定跳过还是真跑
     * （CI 里用 apt 装了 fonts-noto-cjk，是真跑的）。
     */
    public boolean cjkFontAvailable() {
        return ResumePdfFonts.anyAvailable(regularCandidates);
    }

    public byte[] render(String title, String text, ResumeTheme theme) throws IOException {
        try (PDDocument document = new PDDocument()) {
            ResumePdfFonts fonts = ResumePdfFonts.load(document, regularCandidates, boldCandidates);
            try {
                Composer composer = new Composer(document, fonts, theme);
                composer.compose(title, text);
                composer.close();
                composer.paintFooter(nameOf(title));
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                document.save(out);
                return out.toByteArray();
            } finally {
                // 字体集合要活到 save()：子集化是在保存时读取字体数据的
                fonts.close();
            }
        }
    }

    private static String nameOf(String title) {
        return title == null || title.isBlank() ? "简历" : title.strip();
    }

    private static List<String> candidates(String configured, List<String> defaults) {
        List<String> all = new ArrayList<>();
        if (configured != null && !configured.isBlank()) all.add(configured.strip());
        all.addAll(defaults);
        return all;
    }

    private static PDColor rgb(String hex) {
        return new PDColor(new float[] {
                Integer.parseInt(hex.substring(0, 2), 16) / 255f,
                Integer.parseInt(hex.substring(2, 4), 16) / 255f,
                Integer.parseInt(hex.substring(4, 6), 16) / 255f}, PDDeviceRGB.INSTANCE);
    }

    /** 一段文字的成品：去掉不可用字符后的正文 + 需要特殊着色的区间。 */
    private record Content(String text, List<Span> spans) { }

    /** 着色/字体区间：占位符（琥珀加粗）、条目的字段名（"时间："，加粗）。 */
    private record Span(int start, int end, boolean placeholder, boolean label) { }

    /** 一行文字：文本 + 它在整段里的起始下标（用来定位着色区间）。 */
    private record Line(String text, int start) { }

    /** 逐块写进 PDF 的游标：负责换页、行距、圆点与着色。 */
    private static final class Composer {

        private final PDDocument document;
        private final ResumePdfFonts fonts;
        private final PDColor accent;
        private final PDColor accentDark;
        private final Map<Integer, Float> regularWidths = new HashMap<>();
        private final Map<Integer, Float> boldWidths = new HashMap<>();
        private final Map<PDType0Font, Set<Integer>> unsupported = new IdentityHashMap<>();
        private final List<PDPage> pages = new ArrayList<>();

        private PDPageContentStream content;
        private float y;
        private float width;

        Composer(PDDocument document, ResumePdfFonts fonts, ResumeTheme theme) {
            this.document = document;
            this.fonts = fonts;
            this.accent = rgb(theme.accent());
            this.accentDark = rgb(theme.accentDark());
        }

        void compose(String title, String text) throws IOException {
            newPage();
            List<ResumeLayout.Block> body = header(nameOf(title), ResumeLayout.parse(text, title));
            for (int index = 0; index < body.size(); index++) {
                ResumeLayout.Block block = body.get(index);
                switch (block.kind()) {
                    case HEADING -> writeSectionHeading(block);
                    case BULLET -> writeBullet(block.text());
                    case PARAGRAPH -> writeParagraph(block.text(), index == 0
                            || body.get(index - 1).kind() == ResumeLayout.Kind.HEADING);
                }
            }
        }

        /** 顶栏：姓名 + 求职意向 + 强调色分隔线；返回剩下的正文块。 */
        private List<ResumeLayout.Block> header(String name, List<ResumeLayout.Block> blocks) throws IOException {
            pair(name, NAME_SIZE, true, INK);
            y -= 2f;
            int consumed = 0;
            if (!blocks.isEmpty() && isObjective(blocks.get(0))) {
                pair(blocks.get(0).text(), OBJECTIVE_SIZE, false, accentDark);
                consumed = 1;
            }
            y -= 7f;
            rule(MARGIN_LEFT, y, MARGIN_LEFT + width, 1.6f, accent);
            y -= 11f;
            return blocks.subList(consumed, blocks.size());
        }

        /** 紧跟姓名的那行如果是"求职目标：xxx"这种短句，就当顶栏副标题。 */
        private boolean isObjective(ResumeLayout.Block block) {
            if (block.kind() != ResumeLayout.Kind.PARAGRAPH) return false;
            String value = block.text().strip();
            boolean looksLikeObjective = value.contains("求职") || value.contains("应聘") || value.startsWith("目标");
            return value.length() <= 40 && looksLikeObjective;
        }

        private void writeSectionHeading(ResumeLayout.Block block) throws IOException {
            float size = block.level() <= 2 ? SECTION_SIZE : SUB_SECTION_SIZE;
            float lineHeight = size * LINE_FACTOR;
            ensure(SECTION_GAP_BEFORE + lineHeight + 12f);
            y -= SECTION_GAP_BEFORE + lineHeight;

            float barBottom = y + (lineHeight - SECTION_BAR_HEIGHT) / 2f - 1f;
            content.setNonStrokingColor(accent);
            content.addRect(MARGIN_LEFT, barBottom, SECTION_BAR_WIDTH, SECTION_BAR_HEIGHT);
            content.fill();
            show(block.text(), fonts.bold(), size, MARGIN_LEFT + SECTION_BAR_WIDTH + 6.5f, accentDark);

            y -= 5f;
            rule(MARGIN_LEFT, y, MARGIN_LEFT + width, 0.7f, RULE);
            y -= SECTION_GAP_AFTER;
        }

        /** 条目：圆点用矢量画（不受字体有没有 ● 影响），折行与首行文字对齐。 */
        private void writeBullet(String text) throws IOException {
            Content rendered = build(text, BODY_SIZE, fonts.regular());
            if (rendered.text().isBlank()) return;
            List<Line> lines = wrap(rendered.text(), fonts.regular(), BODY_SIZE, width - BULLET_GAP);
            float lineHeight = BODY_SIZE * LINE_FACTOR;
            for (int index = 0; index < lines.size(); index++) {
                ensure(lineHeight);
                y -= lineHeight;
                if (index == 0) {
                    content.setNonStrokingColor(accent);
                    dot(MARGIN_LEFT + 1.2f + BULLET_RADIUS, y + BODY_SIZE * 0.30f, BULLET_RADIUS);
                }
                writeLine(rendered, lines.get(index), BODY_SIZE, MARGIN_LEFT + BULLET_GAP, BODY, false);
            }
            y -= ITEM_GAP;
        }

        /** 普通段落：公司名 / 项目名（紧跟小节标题的）加粗一档，与下面的条目区分开。 */
        private void writeParagraph(String text, boolean entry) throws IOException {
            float size = entry ? ENTRY_SIZE : BODY_SIZE;
            Content rendered = build(text, size, entry ? fonts.bold() : fonts.regular());
            if (rendered.text().isBlank()) return;
            PDType0Font font = entry ? fonts.bold() : fonts.regular();
            List<Line> lines = wrap(rendered.text(), font, size, width);
            if (entry) y -= ENTRY_GAP_BEFORE;
            float lineHeight = size * LINE_FACTOR;
            for (Line line : lines) {
                ensure(lineHeight);
                y -= lineHeight;
                writeLine(rendered, line, size, MARGIN_LEFT, entry ? INK : BODY, entry);
            }
            y -= ITEM_GAP;
        }

        /** 顶栏那种不需要换行的一行文字。 */
        private void pair(String text, float size, boolean bold, PDColor color) throws IOException {
            Content rendered = build(text, size, bold ? fonts.bold() : fonts.regular(), false);
            if (rendered.text().isBlank()) return;
            float lineHeight = size * LINE_FACTOR;
            ensure(lineHeight);
            y -= lineHeight;
            writeLine(rendered, new Line(rendered.text(), 0), size, MARGIN_LEFT, color, bold);
        }

        /** 清洗文本并把"字段名 / 占位符"区间登记下来。 */
        private Content build(String text, float size, PDType0Font font) {
            return build(text, size, font, true);
        }

        /** withLabel=false 时不把"xxx："当字段名强调（顶栏的求职意向不需要）。 */
        private Content build(String text, float size, PDType0Font font, boolean withLabel) {
            StringBuilder full = new StringBuilder();
            List<Span> spans = new ArrayList<>();
            boolean labelDone = false;
            for (ResumeLayout.Piece piece : ResumeLayout.pieces(text)) {
                String clean = sanitize(piece.text(), font);
                int start = full.length();
                full.append(clean);
                if (piece.placeholder()) {
                    spans.add(new Span(start, full.length(), true, false));
                    labelDone = true;
                } else if (withLabel && !labelDone) {
                    // 条目的"时间："这类字段名加粗：只认开头几个字以内的冒号
                    int colon = clean.indexOf('：');
                    if (colon < 0) colon = clean.indexOf(':');
                    if (colon >= 0 && colon <= 7) {
                        spans.add(new Span(start, start + colon + 1, false, true));
                        labelDone = true;
                    }
                }
            }
            return new Content(full.toString(), spans);
        }

        private void writeLine(Content rendered, Line line, float size, float x, PDColor defaultColor, boolean bold)
                throws IOException {
            int to = line.start() + line.text().length();
            int position = line.start();
            while (position < to) {
                Span span = spanAt(rendered.spans(), position);
                int end = span != null ? Math.min(span.end(), to) : nextSpanStart(rendered.spans(), position, to);
                String chunk = rendered.text().substring(position, end);
                boolean placeholder = span != null && span.placeholder();
                boolean label = span != null && span.label();
                PDType0Font font = bold || placeholder || label ? fonts.bold() : fonts.regular();
                show(chunk, font, size, x, placeholder ? PLACEHOLDER : defaultColor);
                x += measure(chunk, font, size);
                position = end;
            }
        }

        private void show(String text, PDType0Font font, float size, float x, PDColor color) throws IOException {
            if (text.isEmpty()) return;
            content.setNonStrokingColor(color);
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(x, y);
            content.showText(text);
            content.endText();
        }

        private List<Line> wrap(String text, PDType0Font font, float size, float maxWidth) throws IOException {
            List<Line> lines = new ArrayList<>();
            int start = 0;
            while (start < text.length()) {
                int end = start;
                int lastSpace = -1;
                float widthAtLastSpace = 0f;
                float used = 0f;
                while (end < text.length()) {
                    int codePoint = text.codePointAt(end);
                    float charWidth = advance(font, codePoint, size);
                    if (used + charWidth > maxWidth && end > start) break;
                    if (codePoint == ' ') {
                        lastSpace = end;
                        widthAtLastSpace = used;
                    }
                    used += charWidth;
                    end += Character.charCount(codePoint);
                }
                // 拉丁文优先断在空格后，中文没有空格就按字断。
                // 必须要求那个空格已经过半行，否则中文条目行里唯一的空格会把整行切成"只剩一个字"。
                boolean usefulSpace = lastSpace > start && widthAtLastSpace > maxWidth * 0.5f;
                int breakAt = end < text.length() && usefulSpace ? lastSpace + 1 : end;
                if (breakAt <= start) breakAt = start + Character.charCount(text.codePointAt(start));
                lines.add(new Line(text.substring(start, breakAt), start));
                start = breakAt;
            }
            return lines;
        }

        private float measure(String text, PDType0Font font, float size) throws IOException {
            float total = 0f;
            for (int index = 0; index < text.length(); ) {
                int codePoint = text.codePointAt(index);
                total += advance(font, codePoint, size);
                index += Character.charCount(codePoint);
            }
            return total;
        }

        /** 单字宽度：getStringWidth 返回的是 1/1000 字号单位，换算成点要再除以 1000。 */
        private float advance(PDType0Font font, int codePoint, float size) throws IOException {
            return widthOf(font, codePoint) * size / 1000f;
        }

        /** 单字宽度按 1000 单位制缓存，避免同一段文字反复查 cmap。 */
        private float widthOf(PDType0Font font, int codePoint) throws IOException {
            Map<Integer, Float> cache = font == fonts.regular() ? regularWidths : boldWidths;
            Float cached = cache.get(codePoint);
            if (cached != null) return cached;
            float value = font.getStringWidth(new String(Character.toChars(codePoint)));
            cache.put(codePoint, value);
            return value;
        }

        /**
         * 字体里没有的字符（emoji 之类）会让 showText 抛异常，先换成 ?，
         * 保证一份带表情的简历也能整份导出来，而不是导出到一半失败。
         */
        private String sanitize(String text, PDType0Font font) {
            StringBuilder out = new StringBuilder(text.length());
            for (int index = 0; index < text.length(); ) {
                int codePoint = text.codePointAt(index);
                index += Character.charCount(codePoint);
                if (codePoint == '\t') {
                    out.append("    ");
                } else if (codePoint < 0x20 || (codePoint >= 0x7F && codePoint <= 0x9F)) {
                    out.append(' ');
                } else if (!canEncode(font, codePoint)) {
                    out.append('?');
                } else {
                    out.appendCodePoint(codePoint);
                }
            }
            return out.toString();
        }

        /**
         * 判断字体里有没有这个字，唯一可靠的办法是"试着编码一次"。
         * 注意不能用 PDType0Font.hasGlyph(int)：它收的是编码后的字形码而不是 Unicode，
         * 拿 Unicode 去问会把「简」「爱」「：」这类常用汉字判成缺失，导出结果就成了一堆问号。
         */
        private boolean canEncode(PDType0Font font, int codePoint) {
            Set<Integer> missing = unsupported.computeIfAbsent(font, key -> new HashSet<>());
            if (missing.contains(codePoint)) return false;
            try {
                font.encode(new String(Character.toChars(codePoint)));
                return true;
            } catch (IOException | RuntimeException exception) {
                missing.add(codePoint);
                log.debug("字体缺少字符 U+{}，导出时替换为 ?",
                        Integer.toHexString(codePoint).toUpperCase(Locale.ROOT));
                return false;
            }
        }

        /** 圆点：四段贝塞尔拼一个整圆，比用字体里的 ● 精致，也不受字体覆盖影响。 */
        private void dot(float centerX, float centerY, float radius) throws IOException {
            float control = radius * 0.5523f;
            content.moveTo(centerX + radius, centerY);
            content.curveTo(centerX + radius, centerY + control, centerX + control, centerY + radius,
                    centerX, centerY + radius);
            content.curveTo(centerX - control, centerY + radius, centerX - radius, centerY + control,
                    centerX - radius, centerY);
            content.curveTo(centerX - radius, centerY - control, centerX - control, centerY - radius,
                    centerX, centerY - radius);
            content.curveTo(centerX + control, centerY - radius, centerX + radius, centerY - control,
                    centerX + radius, centerY);
            content.fill();
        }

        private void rule(float fromX, float atY, float toX, float lineWidth, PDColor color) throws IOException {
            content.setStrokingColor(color);
            content.setLineWidth(lineWidth);
            content.moveTo(fromX, atY);
            content.lineTo(toX, atY);
            content.stroke();
        }

        private void ensure(float needed) throws IOException {
            if (y - needed < MARGIN_BOTTOM) newPage();
        }

        private void newPage() throws IOException {
            if (content != null) content.close();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            pages.add(page);
            content = new PDPageContentStream(document, page);
            width = page.getMediaBox().getWidth() - MARGIN_LEFT - MARGIN_RIGHT;
            y = page.getMediaBox().getHeight() - MARGIN_TOP;
        }

        void close() throws IOException {
            if (content != null) content.close();
            content = null;
        }

        /** 多页时补页脚：细线 + 左侧姓名 + 右侧页码。单页不加，免得像公文。 */
        void paintFooter(String name) throws IOException {
            if (pages.size() < 2) return;
            for (int index = 0; index < pages.size(); index++) {
                PDPage page = pages.get(index);
                float right = page.getMediaBox().getWidth() - MARGIN_RIGHT;
                content = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true);
                try {
                    rule(MARGIN_LEFT, FOOTER_RULE_Y, right, 0.6f, RULE);
                    // show() 用的是游标的 y，画页脚前先把它挪到底部
                    y = FOOTER_RULE_Y - 10f;
                    show(name, fonts.regular(), FOOTER_SIZE, MARGIN_LEFT, MUTED);
                    String pageNumber = "第 " + (index + 1) + " / " + pages.size() + " 页";
                    show(pageNumber, fonts.regular(), FOOTER_SIZE,
                            right - measure(pageNumber, fonts.regular(), FOOTER_SIZE), MUTED);
                } finally {
                    content.close();
                }
            }
            content = null;
        }

        private static Span spanAt(List<Span> spans, int position) {
            for (Span span : spans) {
                if (position >= span.start() && position < span.end()) return span;
            }
            return null;
        }

        private static int nextSpanStart(List<Span> spans, int position, int limit) {
            int next = limit;
            for (Span span : spans) {
                if (span.start() > position && span.start() < next) next = span.start();
            }
            return next;
        }
    }
}
