package com.guangge.Interview.cohort;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CohortHelpRequest(
        @NotNull UUID sessionId,
        @NotNull Integer questionIndex,
        @NotNull UUID answerId,
        @Size(max = 1000) String note) {
}
