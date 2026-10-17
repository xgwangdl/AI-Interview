package com.guangge.Interview.cohort;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PeerReviewRequest(
        @NotNull UUID answerId,
        @Min(1) @Max(5) int clarity,
        @Min(1) @Max(5) int relevance,
        @Min(1) @Max(5) int depth,
        @NotBlank @Size(max = 500) String suggestion) {
}
