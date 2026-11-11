package com.guangge.Interview.jobradar;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record JobLeadPatchRequest(
        @Size(max = 120) String company,
        @Size(max = 120) String role,
        @Size(max = 60) String city,
        @Min(0) @Max(500) Integer salaryMinK,
        @Min(0) @Max(500) Integer salaryMaxK,
        @Size(max = 500) String note,
        @Size(max = 20) String status) { }
