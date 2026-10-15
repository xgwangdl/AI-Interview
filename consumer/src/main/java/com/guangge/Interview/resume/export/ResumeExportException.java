package com.guangge.Interview.resume.export;

/** 导出失败：缺字体、字体文件损坏等环境问题。 */
public class ResumeExportException extends RuntimeException {

    public ResumeExportException(String message) {
        super(message);
    }

    public ResumeExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
