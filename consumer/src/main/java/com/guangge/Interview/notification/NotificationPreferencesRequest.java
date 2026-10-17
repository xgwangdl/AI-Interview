package com.guangge.Interview.notification;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NotificationPreferencesRequest(
        boolean emailEnabled,
        boolean dailyReminder,
        boolean weeklyReport,
        boolean interviewCountdown,
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "提醒时间格式应为 HH:mm")
        String reminderTime,
        @Size(max = 64) String timezone) {
}
