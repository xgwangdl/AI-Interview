package com.guangge.Interview.jobradar;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record SkillProfileUpdateRequest(
        @NotNull @Size(max = 60) List<@Valid SkillItem> skills,
        @Min(0) @Max(60) Integer years,
        @Size(max = 60) String city,
        @Min(0) @Max(500) Integer expectedMinK,
        @Min(0) @Max(500) Integer expectedMaxK) {

    public record SkillItem(
            @NotBlank @Size(max = 80) String name,
            @Min(1) @Max(5) int level,
            @NotNull @DecimalMin("0.1") @DecimalMax("10") BigDecimal weight) { }
}
