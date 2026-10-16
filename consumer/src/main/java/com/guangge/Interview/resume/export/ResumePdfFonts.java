package com.guangge.Interview.resume.export;

import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PDF 用的中文字体：从候选路径里挑第一个能用的，加载成可嵌入子集的 Type0 字体。
 *
 * PDFBox 不会替我们找字体（不像浏览器），而 A4 上写中文必须内嵌字体，否则导出的是乱码或空白。
 * 所以这里给一份"各系统常见路径"的候选清单，并允许用配置覆盖；一个都没命中就明确报错，
 * 而不是生成一份看不懂的 PDF。
 *
 * 说明：只支持 .ttf / .ttc。PDFBox 3 的 PDType0Font 不认 CFF 轮廓的 .otf。
 */
final class ResumePdfFonts implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(ResumePdfFonts.class);

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
        PDType0Font regular = fonts.loadFirst(document, regularCandidates);
        if (regular == null) {
            fonts.close();
            throw new ResumeExportException("服务器上没有可用于 PDF 的中文字体（.ttf/.ttc）。"
                    + "可在 application.properties 里配置 resume.export.pdf-font 指向字体文件后重试。");
        }
        PDType0Font bold = fonts.loadFirst(document, boldCandidates);
        fonts.regular = regular;
        fonts.bold = bold == null ? regular : bold;
        return fonts;
    }

    /** 候选路径里有没有至少一个存在的字体文件（只判断存在，不加载）。 */
    static boolean anyAvailable(List<String> candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank() && Files.isRegularFile(Path.of(candidate))) return true;
        }
        return false;
    }

    private PDType0Font loadFirst(PDDocument document, List<String> candidates) {
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (!Files.isRegularFile(path)) continue;
            try {
                byte[] bytes = Files.readAllBytes(path);
                if (candidate.toLowerCase(Locale.ROOT).endsWith(".ttc")) {
                    TrueTypeCollection collection = new TrueTypeCollection(new ByteArrayInputStream(bytes));
                    opened.add(collection);
                    TrueTypeFont font = firstFont(collection);
                    if (font == null) continue;
                    log.info("PDF 导出使用字体 {}", path);
                    return PDType0Font.load(document, font, true);
                }
                log.info("PDF 导出使用字体 {}", path);
                return PDType0Font.load(document, new ByteArrayInputStream(bytes), true);
            } catch (IOException | RuntimeException exception) {
                log.warn("字体 {} 不可用（{}），继续尝试下一个", path, exception.getMessage());
            }
        }
        return null;
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
            try {
                resource.close();
            } catch (IOException exception) {
                log.debug("关闭字体资源失败：{}", exception.getMessage());
            }
        }
        opened.clear();
    }
}
