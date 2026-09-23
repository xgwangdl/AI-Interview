package com.guangge.Interview.practice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PracticeQuestion(
        @NotBlank @Size(max = 64) String id,
        @NotBlank @Size(max = 2000) String text,
        @Size(max = 100) String category) {
}
