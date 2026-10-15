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
 * 直接排版（PDFBox），不经过 HTML→PDF 中转：依赖只有一个，不牵涉 iText 的 AGPL，
 * 也不用把内容拼成 HTML 再交给一个渲染引擎猜版式。
 *
 * 与需求文档的要求一致：内容就是库里的当前版本，不带任何额外注解；
 * 需要用户补充的占位符用琥珀色标出来，方便打印前先填。
 */
@Component
public class ResumePdfExporter {

    private static final Logger log = LoggerFactory.getLogger(ResumePdfExporter.class);

    private static final float MARGIN_LEFT = 56f;
    private static final float MARGIN_RIGHT = 56f;
    private static final float MARGIN_TOP = 62f;
    private static final float MARGIN_BOTTOM = 58f;

    private static final float TITLE_SIZE = 18f;
    private static final float HEADING_SIZE = 13f;
    private static final float SUB_HEADING_SIZE = 11.5f;
    private static final float BODY_SIZE = 10.5f;
    /** 行距系数：中文正文 1.6 倍左右读起来不挤，也接近 Word 的单倍行距观感。 */
    private static final float LINE_FACTOR = 1.62f;
    /**
     * 项目符号候选：SimHei 里没有 U+2022（•），而雅黑 / Noto / 苹方都有。
     * 按顺序挑第一个字体里存在的，避免整份 PDF 因为一个符号报错。
     */
    private static final List<String> BULLET_MARKS = List.of("• ", "● ", "· ");
    private static final float PARAGRAPH_GAP = 5f;
    private static final float HEADING_GAP = 11f;

    private static final PDColor TEXT_COLOR = new PDColor(new float[] {0.11f, 0.13f, 0.16f}, PDDeviceRGB.INSTANCE);
    private static final PDColor PLACEHOLDER_COLOR = new PDColor(new float[] {0.71f, 0.33f, 0.04f}, PDDeviceRGB.INSTANCE);
    private static final PDColor RULE_COLOR = new PDColor(new float[] {0.78f, 0.8f, 0.82f}, PDDeviceRGB.INSTANCE);

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
        try (PDDocument document = new PDDocument()) {
            ResumePdfFonts fonts = ResumePdfFonts.load(document, regularCandidates, boldCandidates);
            try {
                Composer composer = new Composer(document, fonts);
                composer.compose(title, text);
                composer.close();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                document.save(out);
                return out.toByteArray();
            } finally {
                // 字体集合要活到 save()：子集化是在保存时读取字体数据的
                fonts.close();
            }
        }
    }

    private static List<String> candidates(String configured, List<String> defaults) {
        List<String> all = new ArrayList<>();
        if (configured != null && !configured.isBlank()) all.add(configured.strip());
        all.addAll(defaults);
        return all;
    }

    /** 排版样式：字号 + 字重 + 缩进 + 是否列表项 + 是否居中。 */
    private record Style(float size, boolean bold, float indent, boolean bullet, boolean centered) { }

    /** 一行文字：文本 + 它在整段里的起始下标（用来定位占位符的着色区间）。 */
    private record Line(String text, int start) { }

    /** 逐块写进 PDF 的游标：负责换页、行距与占位符着色。 */
    private static final class Composer {

        private final PDDocument document;
        private final ResumePdfFonts fonts;
        private final Map<Integer, Float> regularWidths = new HashMap<>();
        private final Map<Integer, Float> boldWidths = new HashMap<>();
        private final Map<PDType0Font, Set<Integer>> unsupported = new IdentityHashMap<>();

        private PDPageContentStream content;
        private float y;
        private float width;

        Composer(PDDocument document, ResumePdfFonts fonts) {
            this.document = document;
            this.fonts = fonts;
        }

        void compose(String title, String text) throws IOException {
            newPage();
            String heading = title == null || title.isBlank() ? "简历" : title.strip();
            writeBlock(heading, new Style(TITLE_SIZE, true, 0f, false, true));
            y -= 7f;
            rule();
            y -= 3f;
            for (ResumeLayout.Block block : ResumeLayout.parse(text, title)) {
                switch (block.kind()) {
                    case HEADING -> {
                        y -= HEADING_GAP;
                        writeBlock(block.text(), new Style(
                                block.level() <= 2 ? HEADING_SIZE : SUB_HEADING_SIZE, true, 0f, false, false));
                        if (block.level() <= 2) {
                            // 小节下面走一条细线：和常见简历模板一致，扫读时更容易分段
                            y -= 4f;
                            rule();
                            y -= 3f;
                        } else {
                            y -= 1f;
                        }
                    }
                    case BULLET -> writeBlock(block.text(),
                            new Style(BODY_SIZE, false, 0f, true, false));
                    case PARAGRAPH -> writeBlock(block.text(),
                            new Style(BODY_SIZE, false, 0f, false, false));
                }
                y -= PARAGRAPH_GAP;
            }
        }

        void close() throws IOException {
            if (content != null) content.close();
            content = null;
        }

        private void writeBlock(String text, Style style) throws IOException {
            PDType0Font font = style.bold() ? fonts.bold() : fonts.regular();
            String mark = style.bullet() ? bulletMark(font) : "";
            // 列表项的悬挂缩进按「• 」实测宽度算，折行文字才会和第一行对齐
            float hanging = style.bullet() ? measure(mark, font, style.size()) : 0f;
            StringBuilder full = new StringBuilder();
            List<int[]> placeholders = new ArrayList<>();
            for (ResumeLayout.Piece piece : ResumeLayout.pieces(mark + text)) {
                String clean = sanitize(piece.text(), font);
                int start = full.length();
                full.append(clean);
                if (piece.placeholder()) placeholders.add(new int[] {start, full.length()});
            }
            String value = full.toString();
            if (value.isBlank()) return;

            float lineHeight = style.size() * LINE_FACTOR;
            float limit = width - style.indent() - hanging;
            List<Line> lines = wrap(value, font, style.size(), Math.max(40f, limit));
            for (int index = 0; index < lines.size(); index++) {
                ensure(lineHeight);
                float indent = style.indent() + (index == 0 ? 0f : hanging);
                float x = MARGIN_LEFT + indent;
                if (style.centered()) {
                    float lineWidth = measure(lines.get(index).text(), font, style.size());
                    x = MARGIN_LEFT + Math.max(0f, (width - lineWidth) / 2f);
                }
                y -= lineHeight;
                writeLine(value, placeholders, lines.get(index), font, style.size(), x);
            }
        }

        /** 按占位符区间切段着色：占位符琥珀色，正文墨色。 */
        private void writeLine(String full, List<int[]> placeholders, Line line,
                               PDType0Font font, float size, float x) throws IOException {
            int to = line.start() + line.text().length();
            int position = line.start();
            while (position < to) {
                int[] holder = placeholderAt(placeholders, position);
                int end = holder != null ? Math.min(holder[1], to) : nextPlaceholderStart(placeholders, position, to);
                String chunk = full.substring(position, end);
                content.setNonStrokingColor(holder != null ? PLACEHOLDER_COLOR : TEXT_COLOR);
                showText(chunk, font, size, x);
                x += measure(chunk, font, size);
                position = end;
            }
        }

        private void showText(String text, PDType0Font font, float size, float x) throws IOException {
            if (text.isEmpty()) return;
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
                // 注意：必须要求那个空格已经过半行，否则中文条目行里唯一的空格
                // （项目符号后面那个）会把整行切成"只剩一个 ●"。
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

        private String bulletMark(PDType0Font font) {
            for (String mark : BULLET_MARKS) {
                if (canEncode(font, mark.codePointAt(0))) return mark;
            }
            return "- ";
        }

        private void rule() throws IOException {
            content.moveTo(MARGIN_LEFT, y);
            content.lineTo(MARGIN_LEFT + width, y);
            content.setStrokingColor(RULE_COLOR);
            content.setLineWidth(0.8f);
            content.stroke();
        }

        private void ensure(float needed) throws IOException {
            if (y - needed < MARGIN_BOTTOM) newPage();
        }

        private void newPage() throws IOException {
            if (content != null) content.close();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            width = page.getMediaBox().getWidth() - MARGIN_LEFT - MARGIN_RIGHT;
            y = page.getMediaBox().getHeight() - MARGIN_TOP;
        }

        private static int[] placeholderAt(List<int[]> placeholders, int position) {
            for (int[] range : placeholders) {
                if (position >= range[0] && position < range[1]) return range;
            }
            return null;
        }

        private static int nextPlaceholderStart(List<int[]> placeholders, int position, int limit) {
            int next = limit;
            for (int[] range : placeholders) {
                if (range[0] > position && range[0] < next) next = range[0];
            }
            return next;
        }
    }
}
