package com.guangge.Interview.resume.dto;

import java.time.Instant;
import java.util.UUID;

public record ResumeDetailView(
        UUID id,
        String title,
        String source,
        String fileName,
        int version,
        String rawText,
        Instant updatedAt) {
}
