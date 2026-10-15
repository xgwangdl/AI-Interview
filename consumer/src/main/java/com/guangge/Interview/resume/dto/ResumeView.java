package com.guangge.Interview.resume.dto;

import java.time.Instant;
import java.util.UUID;

/** 列表项：不含正文，避免列表接口把大段文本都拖出来。 */
public record ResumeView(
        UUID id,
        String title,
        String source,
        String fileName,
        int version,
        int characters,
        boolean optimized,
        Instant updatedAt) {
}
