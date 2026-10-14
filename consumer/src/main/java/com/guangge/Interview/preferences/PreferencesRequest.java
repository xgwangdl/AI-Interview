package com.guangge.Interview.preferences;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 个人偏好（目前只有主题色）。 */
public record PreferencesRequest(@NotBlank @Size(max = 32) String theme) {
}
