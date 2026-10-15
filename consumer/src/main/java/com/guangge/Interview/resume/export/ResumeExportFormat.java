package com.guangge.Interview.resume.export;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/** 简历导出格式。参数上给了别名（word / markdown）也认，省得用户猜。 */
public enum ResumeExportFormat {

    MARKDOWN("md", "text/markdown;charset=UTF-8"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PDF("pdf", "application/pdf");

    private final String extension;
    private final String contentType;

    ResumeExportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public static ResumeExportFormat parse(String raw) {
        String value = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "", "md", "markdown" -> MARKDOWN;
            case "docx", "doc", "word" -> DOCX;
            case "pdf" -> PDF;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "不支持的导出格式：" + raw + "（可选 md / docx / pdf）");
        };
    }
}
