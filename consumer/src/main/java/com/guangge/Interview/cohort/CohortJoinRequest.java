package com.guangge.Interview.cohort;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CohortJoinRequest(@NotBlank @Size(max = 16) String inviteCode) {
}
