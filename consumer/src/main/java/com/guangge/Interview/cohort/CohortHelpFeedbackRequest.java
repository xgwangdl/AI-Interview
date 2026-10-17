package com.guangge.Interview.cohort;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CohortHelpFeedbackRequest(@NotBlank @Size(max = 2000) String text) {
}
