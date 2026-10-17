package com.guangge.Interview.cohort;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CohortRequest(@NotBlank @Size(max = 120) String name) {
}
