package com.guangge.Interview.jobradar;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DebriefQuestionUpdateRequest(
        @Size(max = 10) List<@Valid Question> questions) {

    public record Question(
            @NotBlank @Size(min = 5, max = 300) String text,
            @NotBlank @Pattern(regexp = "project_depth|system_design|algorithm|behavioral|domain|other") String category,
            boolean hard,
            @Size(max = 500) String note) { }
}
