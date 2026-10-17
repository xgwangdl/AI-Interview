package com.guangge.Interview.goal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlanItemStatusRequest(@NotBlank @Size(max = 16) String status) {
}
