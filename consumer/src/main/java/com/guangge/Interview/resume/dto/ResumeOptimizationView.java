package com.guangge.Interview.resume.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 一次优化的完整结果，供前端做左右对比。 */
public record ResumeOptimizationView(
        UUID id,
        UUID resumeId,
        String role,
        String status,
        String optimizedText,
        String summary,
        List<ResumeOptimizationResult.Section> sections,
        List<String> placeholders,
        Map<String, List<String>> keywordCoverage,
        String error,
        String model,
        Instant createdAt) {
}
