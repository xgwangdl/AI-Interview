package com.guangge.Interview.practice.resume;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 从 PDF / Word 简历中抽取纯文本，供 AI 出题参考。
 *
 * 文本不落库：出题用完即弃，避免多一份个人数据的保存与清理责任。
 */
@Service
public class ResumeExtractionService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "doc", "docx");
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final int MAX_CHARACTERS = 8000;
    public ResumeExtractView extract(String fileName, long size, InputStream content) {
        if (size <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "简历文件为空");
        }
        if (size > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "简历不能超过 8 MB");
        }
        if (!SUPPORTED_EXTENSIONS.contains(extensionOf(fileName))) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "仅支持 PDF 与 Word 简历");
        }

        String extracted;
        try {
            List<Document> documents = new TikaDocumentReader(new InputStreamResource(content)).read();
            extracted = documents.stream()
                    .map(Document::getText)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("\n"));
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "无法解析这份简历，请换一份文件再试");
        }

        // 注意：这里必须保留换行（见 ResumeTextNormalizer 的说明）。
        // 早期实现把 \s+ 全压成空格，整份简历变成一行，后面所有环节都跟着毁掉。
        String normalized = ResumeTextNormalizer.normalize(extracted);
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "没有从这份简历里读到文字，请换一份可复制的文件");
        }
        boolean truncated = normalized.length() > MAX_CHARACTERS;
        String bounded = truncated ? normalized.substring(0, MAX_CHARACTERS) : normalized;
        return new ResumeExtractView(fileName, bounded.length(), truncated, bounded);
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
