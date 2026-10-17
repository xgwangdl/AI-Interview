package com.guangge.Interview.language;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 手打一轮（识别不可用或环境太吵时的降级入口）。 */
public record LanguageTurnTextRequest(@NotBlank @Size(max = 1000) String text) {
}
