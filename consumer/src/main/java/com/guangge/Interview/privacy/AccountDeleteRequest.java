package com.guangge.Interview.privacy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountDeleteRequest(
        @NotBlank @Size(max = 160) String confirmation) {
}
