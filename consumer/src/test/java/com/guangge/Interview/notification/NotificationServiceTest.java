package com.guangge.Interview.notification;

import com.guangge.Interview.data.Candidates;
import com.guangge.Interview.goal.JobGoalRepository;
import com.guangge.Interview.goal.StudyPlanItemRepository;
import com.guangge.Interview.mail.MailService;
import com.guangge.Interview.progress.WeeklyReportService;
import com.guangge.Interview.repository.CandidatesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private final NotificationPreferenceRepository preferences = mock(NotificationPreferenceRepository.class);
    private final NotificationLogRepository logs = mock(NotificationLogRepository.class);
    private final CandidatesRepository candidates = mock(CandidatesRepository.class);
    private final StudyPlanItemRepository planItems = mock(StudyPlanItemRepository.class);
    private final JobGoalRepository goals = mock(JobGoalRepository.class);
    private final WeeklyReportService weekly = mock(WeeklyReportService.class);
    private final MailService mail = mock(MailService.class);
    private final NotificationService service = new NotificationService(preferences, logs, candidates, planItems,
            goals, weekly, mail, "http://localhost:5173", "test-secret");

    private Candidates candidate;

    @BeforeEach
    void setUp() {
        candidate = new Candidates();
        candidate.setName("smoke");
        candidate.setEmail("smoke@example.com");
        when(candidates.findAll()).thenReturn(List.of(candidate));
        when(candidates.findFirstByName("smoke")).thenReturn(Optional.of(candidate));
        when(preferences.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(logs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("默认偏好为全开、20:00；更新会保存")
    void defaultsAndUpdate() {
        when(preferences.findById("smoke")).thenReturn(Optional.empty());
        var defaults = service.get("smoke");
        assertThat(defaults.emailEnabled()).isTrue();
        assertThat(defaults.reminderTime()).isEqualTo("20:00");

        var saved = service.update("smoke", new NotificationPreferencesRequest(
                true, false, true, false, "21:30", "Asia/Shanghai"));
        assertThat(saved.dailyReminder()).isFalse();
        assertThat(saved.reminderTime()).isEqualTo("21:30");
        verify(preferences).save(any());
    }

    @Test
    @DisplayName("当天完成 2 题后不再发每日提醒")
    void skipsCompletedDays() {
        NotificationPreferenceEntity prefs = prefs();
        when(preferences.findById("smoke")).thenReturn(Optional.of(prefs));
        when(planItems.countByOwnerIdAndPlanDateAndStatus(eq("smoke"), any(), eq("completed"))).thenReturn(2L);

        service.sendDailyReminders();

        verify(mail, never()).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("到点且当天未完成时发一封，并写日志")
    void sendsDailyReminder() {
        NotificationPreferenceEntity prefs = prefs();
        prefs.setReminderTime(LocalTime.now(ZoneId.of("Asia/Shanghai")).minusMinutes(1));
        when(preferences.findById("smoke")).thenReturn(Optional.of(prefs));
        when(planItems.countByOwnerIdAndPlanDateAndStatus(eq("smoke"), any(), eq("completed"))).thenReturn(1L);
        when(logs.existsByOwnerIdAndKindAndSentAtAfter(eq("smoke"), eq("daily"), any())).thenReturn(false);

        service.sendDailyReminders();

        verify(mail).sendHtml(eq("smoke@example.com"), eq("今天还有题没练"), anyString());
        ArgumentCaptor<NotificationLogEntity> captor = ArgumentCaptor.forClass(NotificationLogEntity.class);
        verify(logs).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("sent");
    }

    @Test
    @DisplayName("同一窗口已经发过就不再重复")
    void deduplicates() {
        NotificationPreferenceEntity prefs = prefs();
        prefs.setReminderTime(LocalTime.now(ZoneId.of("Asia/Shanghai")));
        when(preferences.findById("smoke")).thenReturn(Optional.of(prefs));
        when(planItems.countByOwnerIdAndPlanDateAndStatus(eq("smoke"), any(), eq("completed"))).thenReturn(0L);
        when(logs.existsByOwnerIdAndKindAndSentAtAfter(eq("smoke"), eq("daily"), any())).thenReturn(true);

        service.sendDailyReminders();

        verify(mail, never()).sendHtml(anyString(), anyString(), anyString());
    }

    private NotificationPreferenceEntity prefs() {
        NotificationPreferenceEntity entity = new NotificationPreferenceEntity();
        entity.setOwnerId("smoke");
        entity.setEmailEnabled(true);
        entity.setDailyReminder(true);
        return entity;
    }
}
