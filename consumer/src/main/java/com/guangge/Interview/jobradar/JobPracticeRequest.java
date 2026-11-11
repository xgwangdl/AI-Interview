package com.guangge.Interview.jobradar;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record JobPracticeRequest(
        @Min(1) @Max(5) int questionCount,
        @Pattern(regexp = "foundation|standard|advanced") String difficulty,
        @Pattern(regexp = "balanced|direct|supportive") String interviewerStyle) { }
