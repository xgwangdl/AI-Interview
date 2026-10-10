package com.guangge.Interview.practice.resume;

/** 简历解析结果。文本只用于生成面试题，不落库。 */
public record ResumeExtractView(
        String fileName,
        int characters,
        boolean truncated,
        String text) { }
