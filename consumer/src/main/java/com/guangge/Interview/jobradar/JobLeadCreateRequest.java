package com.guangge.Interview.jobradar;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobLeadCreateRequest(
        @Size(max = 120) String company,
        @NotBlank @Size(max = 120) String role,
        @Size(max = 60) String city,
        @Min(0) @Max(500) Integer salaryMinK,
        @Min(0) @Max(500) Integer salaryMaxK,
        @NotBlank @Size(max = 20000) String jdText,
        @Size(max = 500) String sourceUrl) { }
