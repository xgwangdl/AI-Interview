package com.guangge.Interview.resume.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 简历功能的写入类请求。 */
public final class ResumeRequests {

    private ResumeRequests() { }

    public record SaveResume(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 32) String source,
            @Size(max = 255) String fileName,
            @NotBlank @Size(max = 20000) String rawText) {
    }

    public record Optimize(
            @Size(max = 200) String role,
            @NotBlank @Size(max = 10000) String jobDescription) {
    }

    /** 采纳哪些段；空列表表示"全部采用"（前端会二次确认）。 */
    public record Apply(List<Integer> sections) {
    }
}
