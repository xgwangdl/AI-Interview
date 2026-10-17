package com.guangge.Interview.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank @Size(max = 40) String displayName,
        @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 64) String timezone,
        @NotBlank @Pattern(regexp = "standard|large|xlarge") String fontScale,
        boolean reduceMotion,
        @NotBlank @Pattern(regexp = "green|blue|amber") String theme) {
}
