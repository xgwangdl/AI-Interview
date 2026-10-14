package com.guangge.Interview.coding.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 出题请求：一个 spec 对应一道题，前端的题型/语言组合直接映射过来。
 *
 * 例：{@code [{"type":"coding","language":"java"},{"type":"sql"},{"type":"choice"},{"type":"short_answer"}]}
 */
public record CodingPlanRequest(
        @NotBlank @Size(max = 160) String role,
        @Size(max = 200) String company,
        @Size(max = 10000) String jobDescription,
        @Size(max = 32) String difficulty,
        @NotEmpty @Valid List<QuestionSpec> questions) {

    public record QuestionSpec(
            @NotBlank @Size(max = 32) String type,
            @Size(max = 32) String language) {
    }
}
