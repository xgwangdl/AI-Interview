package com.guangge.Interview.practice.plan;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record QuestionPlanRequest(
        @NotBlank @Size(max = 160) String role,
        @Size(max = 200) String company,
        @Size(max = 10000) String jobDescription,
        @Size(max = 255) String resumeName,
        @Size(max = 20000) String resumeText,
        @NotBlank @Size(max = 32) String interviewType,
        @NotBlank @Size(max = 32) String difficulty,
        @NotBlank @Size(max = 32) String interviewerStyle,
        @Min(1) @Max(20) int questionCount,
        /**
         * 本次要重点考察的维度（训练档案里挑出来的短板）。
         * 只影响出题倾向：多出能暴露这些问题的题，不改变题型与总数。
         */
        @Size(max = 5) List<@Size(max = 32) String> focusDimensions) { }
