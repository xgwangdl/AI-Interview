package com.guangge.Interview.jobradar;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkillProfileExtractRequest(@NotBlank @Size(max = 30000) String resumeText) { }
