package com.guangge.Interview.resume.export;

import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PDF 用的中文字体：从候选路径里挑第一个能用的，加载成可嵌入子集的 Type0 字体。
 *
 * PDFBox 不会替我们找字体（不像浏览器），而 A4 上写中文必须内嵌字体，否则导出的是乱码或空白。
 * 所以这里给一份"各系统常见路径"的候选清单，并允许用配置覆盖；一个都没命中就明确报错，
 * 而不是生成一份看不懂的 PDF。
 *
 * 说明：只支持 **TrueType 轮廓**的 .ttf / .ttc。CFF 轮廓的 .otf 与 Noto CJK 的 OTC
 * （Debian/Ubuntu 的 fonts-noto-cjk）能加载却内嵌不了，会被探测挡掉。
 */
final class ResumePdfFonts implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(ResumePdfFonts.class);

    /**
     * 字体"能不能真的内嵌"的探测结果，按路径缓存。
     *
     * Debian / Ubuntu 的 fonts-noto-cjk 是 **CFF 轮廓**的 OTC：PDType0Font 能加载，
     * 但一排版就抛 {@code UnsupportedOperationException: OTF fonts do not have a glyf table}。
     * 光看文件存在、甚至光看 font.encode() 都不够，只有真的排一次、真的 save 一次才知道 ——
     * 所以在选字体时先试排，宁可换一个候选字体，也不要导出到一半才失败。
     */
    private static final Map<String, Boolean> EMBEDDABLE = new ConcurrentHashMap<>();

    /** 只处理 .ttc：集合要活到 save() 之后，否则字体数据会失效。 */
    private final List<Closeable> opened = new ArrayList<>();
    private PDType0Font regular;
    private PDType0Font bold;

    PDType0Font regular() {
        return regular;
    }

    /** 粗体缺失时退化成同一个字重，版式还能看，不因为少一个字体就导不出来。 */
    PDType0Font bold() {
        return bold;
    }

    static ResumePdfFonts load(PDDocument document, List<String> regularCandidates, List<String> boldCandidates)
            throws IOException {
        ResumePdfFonts fonts = new ResumePdfFonts();
        Optional<String> regularPath = firstEmbeddable(regularCandidates);
        if (regularPath.isEmpty()) {
            fonts.close();
            throw new ResumeExportException("服务器上没有可用于 PDF 的中文字体。"
                    + "注意 PDFBox 不能内嵌 CFF（OTF/OTC）轮廓的字体，例如 Debian/Ubuntu 的 fonts-noto-cjk；"
                    + "装 TrueType 的 fonts-wqy-zenhei，或用 resume.export.pdf-font 指定 .ttf / .ttc 后再试。");
        }
        fonts.regular = open(document, regularPath.get(), fonts.opened);
        Optional<String> boldPath = firstEmbeddable(boldCandidates);
        fonts.bold = boldPath.isPresent() ? open(document, boldPath.get(), fonts.opened) : fonts.regular;
        return fonts;
    }

    /** 候选里第一个"能真的内嵌"的字体路径（能加载但排不出来的一律跳过）。 */
    static Optional<String> firstEmbeddable(List<String> candidates) {
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank() || !Files.isRegularFile(Path.of(candidate))) continue;
            if (EMBEDDABLE.computeIfAbsent(candidate, ResumePdfFonts::probe)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    /** 用一个小文档试排 + 试保存：只有走完子集化才知道字体能不能内嵌。 */
    private static boolean probe(String candidate) {
        Path path = Path.of(candidate);
        List<Closeable> openedForProbe = new ArrayList<>();
        try (PDDocument document = new PDDocument()) {
            PDType0Font font = open(document, candidate, openedForProbe);
            if (font == null) return false;
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(font, 12);
                content.newLineAtOffset(48, 700);
                content.showText("中文简历");
                content.endText();
            }
            document.save(new ByteArrayOutputStream());
            log.info("PDF 导出可用字体 {}", path);
            return true;
        } catch (IOException | RuntimeException exception) {
            log.info("字体 {} 不能内嵌（{}），换下一个候选", path, exception.getMessage());
            return false;
        } finally {
            for (Closeable resource : openedForProbe) closeQuietly(resource);
        }
    }

    /** 把某个路径加载成可嵌入的 Type0 字体；.ttc 需要把集合留到 save() 之后。 */
    private static PDType0Font open(PDDocument document, String candidate, List<Closeable> opened)
            throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of(candidate));
        if (candidate.toLowerCase(Locale.ROOT).endsWith(".ttc")) {
            TrueTypeCollection collection = new TrueTypeCollection(new ByteArrayInputStream(bytes));
            opened.add(collection);
            TrueTypeFont font = firstFont(collection);
            return font == null ? null : PDType0Font.load(document, font, true);
        }
        return PDType0Font.load(document, new ByteArrayInputStream(bytes), true);
    }

    private static TrueTypeFont firstFont(TrueTypeCollection collection) throws IOException {
        TrueTypeFont[] holder = new TrueTypeFont[1];
        collection.processAllFonts(font -> {
            if (holder[0] == null) holder[0] = font;
        });
        return holder[0];
    }

    @Override
    public void close() {
        for (Closeable resource : opened) {
            closeQuietly(resource);
        }
        opened.clear();
    }

    private static void closeQuietly(Closeable resource) {
        try {
            resource.close();
        } catch (IOException exception) {
            log.debug("关闭字体资源失败：{}", exception.getMessage());
        }
    }
}
