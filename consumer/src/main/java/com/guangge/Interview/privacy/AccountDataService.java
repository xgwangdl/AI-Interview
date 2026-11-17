package com.guangge.Interview.privacy;

import com.guangge.Interview.practice.storage.MediaStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Account-level data export and physical deletion for the supported Flyway-managed modules. */
@Service
public class AccountDataService {

    private static final Logger log = LoggerFactory.getLogger(AccountDataService.class);
    private static final int MAX_UPLOAD_CHUNKS = 1000;

    private final JdbcTemplate jdbc;
    private final MediaStorage media;

    public AccountDataService(JdbcTemplate jdbc, MediaStorage media) {
        this.jdbc = jdbc;
        this.media = media;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> export(String ownerId) {
        requireAccount(ownerId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schemaVersion", 1);
        data.put("generatedAt", Instant.now());
        data.put("account", jdbc.queryForMap("""
                select candidate_name as login_name, display_name, email, timezone, theme, language,
                       font_scale, reduce_motion, skill_profile_json::text as skill_profile_json, created_at
                  from candidates where candidate_name = ?
                """, ownerId));
        data.put("privacyConsents", rows("""
                select consent_type, version, accepted_at from privacy_consents
                 where owner_id = ? order by accepted_at
                """, ownerId));
        data.put("practiceSessions", rows("""
                select id, goal_id, role, company, job_description, resume_name, interview_type, difficulty,
                       interviewer_style, question_count, duration_minutes, follow_up_enabled, mode,
                       paused_count, questions_json, status, created_at, started_at, completed_at,
                       retention_until
                  from practice_sessions where owner_id = ? order by created_at
                """, ownerId));
        data.put("practiceAnswers", rows("""
                select a.id, a.session_id, a.question_index, a.question_id, a.attempt, a.is_current,
                       a.media_content_type, a.media_size_bytes, a.duration_seconds,
                       a.started_at_offset_seconds, a.transcript_status, a.transcript_text,
                       a.transcript_original, a.transcript_edited_at, a.evaluation_status,
                       a.evaluation_json, a.created_at
                  from practice_answers a join practice_sessions s on s.id = a.session_id
                 where s.owner_id = ? order by s.created_at, a.question_index, a.attempt
                """, ownerId));
        data.put("practiceScoreArchive", rows("""
                select session_id, session_created_at, question_index, question_id, role, difficulty,
                       overall_score, relevance, completeness, professionalism, star_structure, logic,
                       evaluated_at
                  from practice_score_archive where owner_id = ? order by session_created_at
                """, ownerId));
        data.put("jobGoals", rows("select * from job_goals where owner_id = ? order by created_at", ownerId));
        data.put("studyPlans", rows("select * from study_plans where owner_id = ? order by created_at", ownerId));
        data.put("studyPlanItems", rows("""
                select id, plan_id, plan_date, kind, session_id, question_ref, title, estimated_minutes,
                       status, position, created_at, completed_at, assignment_id
                  from study_plan_items where owner_id = ? order by plan_date, position
                """, ownerId));
        data.put("weeklyReports", rows("""
                select week_start, week_end, report_json, generated_at from weekly_reports
                 where owner_id = ? order by week_start
                """, ownerId));
        data.put("notificationPreferences", rows("""
                select email_enabled, daily_reminder, weekly_report, interview_countdown, reminder_time,
                       timezone, updated_at from notification_preferences where owner_id = ?
                """, ownerId));
        data.put("notificationLog", rows("""
                select kind, channel, sent_at, status, error from notification_log
                 where owner_id = ? order by sent_at
                """, ownerId));
        data.put("languageSessions", rows("""
                select id, language, scenario, level, target, status, turn_count, max_turns, report_json,
                       created_at, finished_at, retention_until from language_sessions
                 where owner_id = ? order by created_at
                """, ownerId));
        data.put("languageTurns", rows("""
                select t.id, t.session_id, t.turn_index, t.audio_content_type, t.audio_ms, t.transcript,
                       t.reply_text, t.feedback_json, t.asr_ms, t.llm_ms, t.tts_ms, t.total_ms, t.status,
                       t.created_at
                  from language_turns t join language_sessions s on s.id = t.session_id
                 where s.owner_id = ? order by s.created_at, t.turn_index
                """, ownerId));
        data.put("codingSessions", rows("""
                select id, role, company, job_description, difficulty, status, question_ids_json,
                       retention_until, created_at, updated_at from coding_sessions
                 where owner_id = ? order by created_at
                """, ownerId));
        data.put("codeSubmissions", rows("""
                select id, session_id, question_id, answer_text, status, passed_cases, total_cases,
                       runtime_ms, stdout, stderr, compile_output, case_results_json, ai_review_json,
                       created_at, updated_at from code_submissions
                 where owner_id = ? order by created_at
                """, ownerId));
        data.put("resumes", rows("""
                select id, title, source, file_name, raw_text, version, created_at, updated_at
                  from resumes where owner_id = ? order by created_at
                """, ownerId));
        data.put("resumeOptimizations", rows("""
                select o.id, o.resume_id, o.role, o.job_description, o.status, o.optimized_text,
                       o.changes_json, o.placeholders_json, o.error, o.model, o.created_at
                  from resume_optimizations o where o.owner_id = ? order by o.created_at
                """, ownerId));
        data.put("cohortMemberships", rows("""
                select m.cohort_id, m.role, m.share_scores, m.peer_review_enabled, m.joined_at
                  from cohort_members m where m.owner_id = ? order by m.joined_at
                """, ownerId));
        data.put("cohortHelpRequests", rows("""
                select id, cohort_id, session_id, question_index, answer_id, note, status, feedback_text,
                       coach_id, created_at, answered_at from cohort_help_requests
                 where owner_id = ? or coach_id = ? order by created_at
                """, ownerId, ownerId));
        data.put("peerReviews", rows("""
                select cohort_id, reviewer_id, target_owner_id, answer_id, clarity, relevance, depth,
                       suggestion, created_at from peer_reviews
                 where reviewer_id = ? or target_owner_id = ? order by created_at
                """, ownerId, ownerId));
        data.put("jobLeads", rows("select * from job_leads where owner_id = ? order by created_at", ownerId));
        data.put("jobEvaluations", rows("select * from job_evaluations where owner_id = ? order by created_at", ownerId));
        data.put("interviewDebriefs", rows("select * from interview_debriefs where owner_id = ? order by interview_date", ownerId));
        data.put("trainingWeakPoints", rows("select * from training_weak_points where owner_id = ? order by created_at", ownerId));
        data.put("jobAgentRuns", rows("select * from job_agent_runs where owner_id = ? order by created_at", ownerId));
        return data;
    }

    @Transactional
    public void deleteAccount(String ownerId, String confirmation) {
        if (confirmation == null || !ownerId.equals(confirmation.strip())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入当前用户名以确认删除");
        }
        requireAccount(ownerId);

        List<String> mediaKeys = new ArrayList<>();
        List<UUID> answerIds = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList("""
                select a.id as answer_id, a.media_key from practice_answers a
                  join practice_sessions s on s.id = a.session_id where s.owner_id = ?
                """, ownerId)) {
            add(mediaKeys, row.get("media_key"));
            // 只有未完成上传的答案才可能残留 uploads/{answerId}/chunk-N；已完成的上传在合并时已清空分片。
            if (row.get("media_key") == null) {
                Object answerId = row.get("answer_id");
                if (answerId instanceof UUID uuid) answerIds.add(uuid);
                else if (answerId != null) answerIds.add(UUID.fromString(answerId.toString()));
            }
        }
        for (Map<String, Object> row : jdbc.queryForList("""
                select t.audio_key, t.reply_audio_key,
                       'lang-' || s.id || '-opening' as opening_key
                  from language_sessions s left join language_turns t on t.session_id = s.id
                 where s.owner_id = ?
                """, ownerId)) {
            add(mediaKeys, row.get("audio_key"));
            add(mediaKeys, row.get("reply_audio_key"));
            add(mediaKeys, row.get("opening_key"));
        }

        List<String> failedKeys = new ArrayList<>();
        for (String key : mediaKeys) {
            try {
                media.delete(key);
            } catch (IOException exception) {
                failedKeys.add(key);
                log.warn("账号删除时媒体清理失败 owner={} key={}: {}", ownerId, key, exception.getMessage());
            }
        }
        for (UUID answerId : answerIds) {
            for (int index = 0; index < MAX_UPLOAD_CHUNKS; index++) {
                String key = "uploads/" + answerId + "/chunk-" + index;
                try {
                    media.delete(key);
                } catch (IOException exception) {
                    failedKeys.add(key);
                }
            }
        }
        if (!failedKeys.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "部分录像尚未清理，账号未删除；请稍后重试");
        }

        deleteOwnedRows(ownerId);
    }

    private void deleteOwnedRows(String ownerId) {
        update("delete from peer_reviews where reviewer_id = ? or target_owner_id = ?", ownerId, ownerId);
        update("delete from cohort_help_requests where owner_id = ? or coach_id = ?", ownerId, ownerId);
        update("delete from cohort_members where owner_id = ?", ownerId);
        update("delete from cohorts where owner_id = ?", ownerId);
        update("delete from study_plan_items where owner_id = ?", ownerId);
        update("delete from study_plans where owner_id = ?", ownerId);
        update("delete from job_goals where owner_id = ?", ownerId);
        update("delete from practice_sessions where owner_id = ?", ownerId);
        update("delete from practice_score_archive where owner_id = ?", ownerId);
        update("delete from coding_sessions where owner_id = ?", ownerId);
        update("delete from language_sessions where owner_id = ?", ownerId);
        update("delete from resumes where owner_id = ?", ownerId);
        update("delete from job_agent_runs where owner_id = ?", ownerId);
        update("delete from training_weak_points where owner_id = ?", ownerId);
        update("delete from interview_debriefs where owner_id = ?", ownerId);
        update("delete from job_evaluations where owner_id = ?", ownerId);
        update("delete from job_leads where owner_id = ?", ownerId);
        update("delete from weekly_reports where owner_id = ?", ownerId);
        update("delete from notification_log where owner_id = ?", ownerId);
        update("delete from notification_preferences where owner_id = ?", ownerId);
        update("delete from privacy_consents where owner_id = ?", ownerId);
        update("delete from candidates where candidate_name = ?", ownerId);
    }

    private void requireAccount(String ownerId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from candidates where candidate_name = ?", Integer.class, ownerId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号不存在或已删除");
        }
    }

    private List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    private void update(String sql, Object... args) {
        jdbc.update(sql, args);
    }

    private static void add(List<String> keys, Object value) {
        if (value != null && !value.toString().isBlank()) keys.add(value.toString());
    }
}
