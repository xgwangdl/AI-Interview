package com.guangge.Interview.language;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 开一场外语会话：语言 + 场景 + 等级（题量可选，默认 8 轮）。 */
public record LanguageSessionRequest(
        @NotBlank @Size(max = 8) String language,
        @NotBlank @Size(max = 32) String scenario,
        @Size(max = 16) String level,
        @Min(1) @Max(LanguageCatalog.MAX_TURNS_LIMIT) Integer maxTurns) {
}
