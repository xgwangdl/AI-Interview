package com.guangge.Interview.resume.dto;

import java.util.List;

/** 模型必须输出的结构（从零创建 / 追问补全共用）。 */
public record ResumeDraftResult(
        String resumeText,
        List<String> questions,
        List<String> placeholders) {
}
