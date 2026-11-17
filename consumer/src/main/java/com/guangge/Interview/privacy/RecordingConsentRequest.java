package com.guangge.Interview.privacy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecordingConsentRequest(
        @NotBlank @Size(max = 64) String version) {
}
