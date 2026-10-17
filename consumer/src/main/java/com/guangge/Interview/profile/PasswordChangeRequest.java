package com.guangge.Interview.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank @Size(min = 6, max = 100) String newPassword) {
}
