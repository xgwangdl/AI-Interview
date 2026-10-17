package com.guangge.Interview.goal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record JobGoalRequest(
        @Size(max = 200) String company,
        @NotBlank @Size(max = 160) String role,
        @Size(max = 10000) String jobDescription,
        UUID resumeId,
        @Size(max = 32) String stage,
        Instant interviewAt,
        @Size(max = 16) String status,
        @Size(max = 2000) String notes) {
}
