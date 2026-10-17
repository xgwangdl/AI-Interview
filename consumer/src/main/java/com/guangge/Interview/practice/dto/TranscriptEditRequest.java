package com.guangge.Interview.practice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 人工修正转写；只改文本，不改视频。 */
public record TranscriptEditRequest(@NotBlank @Size(max = 20000) String text) {
}
