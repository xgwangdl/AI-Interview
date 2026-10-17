package com.guangge.Interview.cohort;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record AssignmentRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @Min(1) @Max(14) int days,
        @Min(1) @Max(5) int itemsPerDay,
        Instant dueAt) {
}
