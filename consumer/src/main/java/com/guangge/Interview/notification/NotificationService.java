package com.guangge.Interview.notification;

import com.guangge.Interview.data.Candidates;
import com.guangge.Interview.goal.JobGoalEntity;
import com.guangge.Interview.goal.JobGoalRepository;
import com.guangge.Interview.goal.StudyPlanItemRepository;
import com.guangge.Interview.mail.MailService;
import com.guangge.Interview.progress.WeeklyReportService;
import com.guangge.Interview.progress.WeeklyReportView;
import com.guangge.Interview.repository.CandidatesRepository;
import com.guangge.Interview.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 提醒与周报邮件。
 *
 * 定时任务只负责"到点检查"，真正的去重靠 `notification_log`：
 * 同一账号同一种提醒，在对应窗口内只发一次。
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalTime WEEKLY_TIME = LocalTime.of(9, 0);

    private final NotificationPreferenceRepository preferences;
    private final NotificationLogRepository logs;
    private final CandidatesRepository candidates;
    private final StudyPlanItemRepository planItems;
    private final JobGoalRepository goals;
    private final WeeklyReportService weeklyReports;
    private final MailService mail;
    private final String publicBaseUrl;
    private final String tokenSecret;

    public NotificationService(
            NotificationPreferenceRepository preferences, NotificationLogRepository logs,
            CandidatesRepository candidates, StudyPlanItemRepository planItems, JobGoalRepository goals,
            WeeklyReportService weeklyReports, MailService mail,
            @Value("${app.public-base-url:http://localhost:5173}") String publicBaseUrl,
            @Value("${auth.token.secret}") String tokenSecret) {
        this.preferences = preferences;
        this.logs = logs;
        this.candidates = candidates;
        this.planItems = planItems;
        this.goals = goals;
        this.weeklyReports = weeklyReports;
        this.mail = mail;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.tokenSecret = tokenSecret;
    }

    @Transactional(readOnly = true)
    public NotificationPreferencesView get(String ownerId) {
        return view(requirePreferences(ownerId));
    }

    @Transactional
    public NotificationPreferencesView update(String ownerId, NotificationPreferencesRequest request) {
        NotificationPreferenceEntity entity = preferences.findById(ownerId)
                .orElseGet(() -> newEntity(ownerId));
        entity.setEmailEnabled(request.emailEnabled());
        entity.setDailyReminder(request.dailyReminder());
        entity.setWeeklyReport(request.weeklyReport());
        entity.setInterviewCountdown(request.interviewCountdown());
        if (request.reminderTime() != null && !request.reminderTime().isBlank()) {
            entity.setReminderTime(LocalTime.parse(request.reminderTime()));
        }
        if (request.timezone() != null && !request.timezone().isBlank()) {
            entity.setTimezone(request.timezone());
        }
        entity.setUpdatedAt(Instant.now());
        return view(preferences.save(entity));
    }

    /** 设置页“发一封测试邮件”。不走每日去重，方便用户确认配置。 */
    @Transactional
    public boolean sendTest(String ownerId) {
        String email = candidates.findFirstByName(ownerId).map(Candidates::getEmail).orElse(null);
        if (email == null || email.isBlank()) return false;
        send(ownerId, "test", "Light-Interview 提醒测试",
                emailHtml("提醒测试", "如果你收到这封邮件，说明每日提醒与周报可以正常送达。",
                        publicBaseUrl + "/settings/notifications", "打开提醒设置"));
        return true;
    }

    @Scheduled(cron = "0 */15 * * * *", zone = "Asia/Shanghai")
    public void sendDailyReminders() {
        LocalDate today = LocalDate.now(ZONE);
        LocalTime now = LocalTime.now(ZONE);
        for (Candidates candidate : candidates.findAll()) {
            String owner = candidate.getName();
            NotificationPreferenceEntity prefs = requirePreferences(owner);
            if (!prefs.isEmailEnabled() || !prefs.isDailyReminder()) continue;
            if (Math.abs(Duration.between(now, prefs.getReminderTime()).toMinutes()) > 7) continue;
            long completed = planItems.countByOwnerIdAndPlanDateAndStatus(owner, today, "completed");
            if (completed >= 2) continue;
            if (alreadySent(owner, "daily", today.atStartOfDay(ZONE).toInstant())) continue;
            String html = emailHtml("今天还有题没练",
                    "今天已完成 " + completed + " 题。再练 1-2 题就能保持连续记录。",
                    publicBaseUrl + "/today", "继续今天训练");
            send(owner, "daily", "今天还有题没练", html);
        }
    }

    @Scheduled(cron = "0 0 9 * * MON", zone = "Asia/Shanghai")
    public void sendWeeklyReports() {
        LocalDate lastWeek = LocalDate.now(ZONE).minusWeeks(1)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (Candidates candidate : candidates.findAll()) {
            String owner = candidate.getName();
            NotificationPreferenceEntity prefs = requirePreferences(owner);
            if (!prefs.isEmailEnabled() || !prefs.isWeeklyReport()) continue;
            if (alreadySent(owner, "weekly", lastWeek.atStartOfDay(ZONE).toInstant())) continue;
            WeeklyReportView report = weeklyReports.report(owner, lastWeek);
            if (report.sessions() == 0 && report.planTotal() == 0) continue;
            String html = emailHtml("上周训练周报",
                    "完成 " + report.planCompleted() + "/" + report.planTotal() + " 个训练项，"
                            + report.sessions() + " 场练习，综合 "
                            + (report.averageScore() == null ? "暂无分数" : report.averageScore() + " 分") + "。",
                    publicBaseUrl + "/progress/weekly?week=" + report.weekStart(), "查看完整周报");
            send(owner, "weekly", "上周训练周报", html);
        }
    }

    @Scheduled(cron = "0 5 9 * * *", zone = "Asia/Shanghai")
    public void sendInterviewCountdowns() {
        LocalDate today = LocalDate.now(ZONE);
        for (Candidates candidate : candidates.findAll()) {
            String owner = candidate.getName();
            NotificationPreferenceEntity prefs = requirePreferences(owner);
            if (!prefs.isEmailEnabled() || !prefs.isInterviewCountdown()) continue;
            for (JobGoalEntity goal : goals.findActiveForPlan(owner)) {
                if (goal.getInterviewAt() == null) continue;
                long days = ChronoUnit.DAYS.between(today, goal.getInterviewAt().atZone(ZONE).toLocalDate());
                if (days != 7 && days != 3 && days != 1) continue;
                if (alreadySent(owner, "countdown", today.atStartOfDay(ZONE).toInstant())) continue;
                String html = emailHtml("距面试还有 " + days + " 天",
                        (goal.getCompany() == null ? "" : goal.getCompany() + " · ") + goal.getRole()
                                + " 的面试就在 " + days + " 天后，今天建议练 3 题。",
                        publicBaseUrl + "/goals/" + goal.getId(), "查看目标与计划");
                send(owner, "countdown", "距面试还有 " + days + " 天", html);
            }
        }
    }

    /** 邮件底部的一键退订：用 HMAC 防止别人改 owner 关掉你的提醒。 */
    public String unsubscribeUrl(String owner) {
        return publicBaseUrl + "/api/v1/notifications/unsubscribe?owner="
                + java.net.URLEncoder.encode(owner, StandardCharsets.UTF_8)
                + "&token=" + unsubscribeToken(owner);
    }

    @Transactional
    public boolean unsubscribe(String owner, String token) {
        if (!unsubscribeToken(owner).equals(token)) return false;
        NotificationPreferenceEntity entity = preferences.findById(owner).orElseGet(() -> newEntity(owner));
        entity.setEmailEnabled(false);
        entity.setUpdatedAt(Instant.now());
        preferences.save(entity);
        return true;
    }

    private void send(String owner, String kind, String subject, String html) {
        String email = candidates.findFirstByName(owner).map(Candidates::getEmail).orElse(null);
        if (email == null || email.isBlank()) {
            record(owner, kind, "skipped", "账号没有邮箱");
            return;
        }
        try {
            String withFooter = html.replace("</body>", "<p style=\"color:#98a29d;font-size:12px\">"
                    + "<a href=\"" + unsubscribeUrl(owner) + "\">不想再收到提醒？点这里关闭邮件</a></p></body>");
            mail.sendHtml(email, subject, withFooter);
            record(owner, kind, "sent", null);
        } catch (RuntimeException exception) {
            log.warn("提醒邮件发送失败 owner={} kind={}: {}", owner, kind, LogSanitizer.sanitize(exception.getMessage()));
            record(owner, kind, "failed", LogSanitizer.truncate(exception.getMessage(), 300));
        }
    }

    private boolean alreadySent(String owner, String kind, Instant after) {
        return logs.existsByOwnerIdAndKindAndSentAtAfter(owner, kind, after);
    }

    private void record(String owner, String kind, String status, String error) {
        NotificationLogEntity entity = new NotificationLogEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerId(owner);
        entity.setKind(kind);
        entity.setChannel("email");
        entity.setSentAt(Instant.now());
        entity.setStatus(status);
        entity.setError(error);
        logs.save(entity);
    }

    private NotificationPreferenceEntity requirePreferences(String owner) {
        return preferences.findById(owner).orElseGet(() -> newEntity(owner));
    }

    private static NotificationPreferenceEntity newEntity(String owner) {
        NotificationPreferenceEntity entity = new NotificationPreferenceEntity();
        entity.setOwnerId(owner);
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private static NotificationPreferencesView view(NotificationPreferenceEntity entity) {
        return new NotificationPreferencesView(entity.isEmailEnabled(), entity.isDailyReminder(),
                entity.isWeeklyReport(), entity.isInterviewCountdown(),
                entity.getReminderTime().toString(), entity.getTimezone());
    }

    private String unsubscribeToken(String owner) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(owner.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成退订令牌", exception);
        }
    }

    private String emailHtml(String title, String body, String url, String action) {
        return "<html><body style=\"font-family:Arial,'Microsoft YaHei',sans-serif;color:#33413a\">"
                + "<h2 style=\"font-size:20px\">" + title + "</h2>"
                + "<p style=\"line-height:1.8\">" + body + "</p>"
                + "<p><a href=\"" + url + "\" style=\"display:inline-block;padding:9px 15px;"
                + "background:#b45d28;color:#fff;text-decoration:none;border-radius:5px\">"
                + action + "</a></p></body></html>";
    }
}
