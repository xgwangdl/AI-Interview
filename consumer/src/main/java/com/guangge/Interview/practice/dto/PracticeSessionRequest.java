package com.guangge.Interview.practice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PracticeSessionRequest(
        @NotBlank @Size(max = 160) String role,
        @Size(max = 200) String company,
        @Size(max = 10000) String jobDescription,
        @Size(max = 255) String resumeName,
        @NotBlank @Size(max = 32) String interviewType,
        @NotBlank @Size(max = 32) String difficulty,
        @NotBlank @Size(max = 32) String interviewerStyle,
        @Min(1) @Max(20) int questionCount,
        @Min(1) @Max(240) int durationMinutes,
        @NotNull @NotEmpty @Size(max = 20) List<@Valid PracticeQuestion> questions) {
}
