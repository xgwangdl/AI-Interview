package com.guangge.Interview.resume.export;

/** 导出结果：字节 + 格式（格式决定扩展名与 Content-Type）。 */
public record ExportedResume(String fileName, byte[] body, ResumeExportFormat format) {

    public String contentType() {
        return format.contentType();
    }
}
