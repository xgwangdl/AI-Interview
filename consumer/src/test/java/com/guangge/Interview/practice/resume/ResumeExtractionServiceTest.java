package com.guangge.Interview.practice.resume;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeExtractionServiceTest {

    private final ResumeExtractionService service = new ResumeExtractionService();

    @Test
    void extractsTextFromAWordDocument() throws IOException {
        byte[] resume = docx("张三 5 年 Java 后端经验，负责订单系统的设计与实现。");

        ResumeExtractView view = service.extract("resume.docx", resume.length, new ByteArrayInputStream(resume));

        assertTrue(view.text().contains("订单系统的设计与实现"), view.text());
        assertFalse(view.truncated());
        assertEquals(view.text().length(), view.characters());
    }

    @Test
    void keepsParagraphBreaksInsteadOfFlatteningTheResume() throws IOException {
        byte[] resume = docx("教育背景\n上海科技大学\n\n工作经历\n负责后端接口开发，接口耗时从 800ms 降到 120ms。");

        ResumeExtractView view = service.extract("resume.docx", resume.length, new ByteArrayInputStream(resume));

        // 曾经这里会把所有换行压成空格，整份简历变成一行，后续优化与导出跟着一起毁掉
        assertTrue(view.text().contains("教育背景\n上海科技大学"), view.text());
        assertTrue(view.text().contains("工作经历\n负责后端接口开发"), view.text());
    }

    @Test
    void truncatesVeryLongResumes() throws IOException {
        byte[] resume = docx("经历 ".repeat(5000));

        ResumeExtractView view = service.extract("resume.docx", resume.length, new ByteArrayInputStream(resume));

        assertTrue(view.truncated());
        assertEquals(8000, view.characters());
        assertEquals(8000, view.text().length());
    }

    @Test
    void rejectsUnsupportedFileTypes() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.extract("resume.exe", 10, new ByteArrayInputStream(new byte[10])));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, error.getStatusCode());
    }

    @Test
    void rejectsEmptyFiles() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.extract("resume.pdf", 0, new ByteArrayInputStream(new byte[0])));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rejectsOversizedFiles() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.extract("resume.pdf", 9L * 1024 * 1024, new ByteArrayInputStream(new byte[1])));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.getStatusCode());
    }

    @Test
    void rejectsDocumentsWithoutReadableText() throws IOException {
        byte[] resume = docx("   ");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.extract("resume.docx", resume.length, new ByteArrayInputStream(resume)));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatusCode());
    }

    /** 最小可解析的 .docx：只要 OOXML 包结构完整，Tika 就能取出正文。 */
    private static byte[] docx(String bodyText) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            write(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml"
                        ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
            write(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1"
                        Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                        Target="word/document.xml"/>
                    </Relationships>
                    """);
            write(zip, "word/document.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body><w:p><w:r><w:t>%s</w:t></w:r></w:p></w:body>
                    </w:document>
                    """.formatted(bodyText));
        }
        return out.toByteArray();
    }

    private static void write(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
