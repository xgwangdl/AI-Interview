package com.guangge.Interview.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreferenceEntity {

    @Id
    @Column(name = "owner_id", length = 160)
    private String ownerId;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    @Column(name = "daily_reminder", nullable = false)
    private boolean dailyReminder = true;

    @Column(name = "weekly_report", nullable = false)
    private boolean weeklyReport = true;

    @Column(name = "interview_countdown", nullable = false)
    private boolean interviewCountdown = true;

    @Column(name = "reminder_time", nullable = false)
    private LocalTime reminderTime = LocalTime.of(20, 0);

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "Asia/Shanghai";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public void setEmailEnabled(boolean emailEnabled) { this.emailEnabled = emailEnabled; }
    public boolean isDailyReminder() { return dailyReminder; }
    public void setDailyReminder(boolean dailyReminder) { this.dailyReminder = dailyReminder; }
    public boolean isWeeklyReport() { return weeklyReport; }
    public void setWeeklyReport(boolean weeklyReport) { this.weeklyReport = weeklyReport; }
    public boolean isInterviewCountdown() { return interviewCountdown; }
    public void setInterviewCountdown(boolean interviewCountdown) { this.interviewCountdown = interviewCountdown; }
    public LocalTime getReminderTime() { return reminderTime; }
    public void setReminderTime(LocalTime reminderTime) { this.reminderTime = reminderTime; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
