package com.guangge.Interview.notification;

public record NotificationPreferencesView(
        boolean emailEnabled,
        boolean dailyReminder,
        boolean weeklyReport,
        boolean interviewCountdown,
        String reminderTime,
        String timezone) {
}
