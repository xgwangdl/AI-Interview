package com.guangge.Interview.practice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PracticeAnswerRequest(
        @Min(0) int questionIndex,
        @NotBlank @Size(max = 64) String questionId,
        @Min(0) int durationSeconds,
        @Min(0) int startedAtOffsetSeconds) {
}
