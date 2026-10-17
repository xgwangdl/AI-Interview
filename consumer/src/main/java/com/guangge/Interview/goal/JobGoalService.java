package com.guangge.Interview.goal;

import com.guangge.Interview.practice.dto.PracticeSessionView;
import com.guangge.Interview.practice.entity.PracticeSessionEntity;
import com.guangge.Interview.practice.entity.PracticeScoreEntity;
import com.guangge.Interview.practice.repository.PracticeScoreRepository;
import com.guangge.Interview.practice.repository.PracticeSessionRepository;
import com.guangge.Interview.practice.repository.PracticeAnswerRepository;
import com.guangge.Interview.practice.service.PracticeSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 求职目标：所有练习的锚点。
 *
 * 目标不是"再填一次岗位"，而是让首页能回答"距面试几天、今天练什么、练了多少"。
 */
@Service
public class JobGoalService {

    public static final Set<String> STAGES = Set.of("oa", "phone", "technical", "system_design", "hr", "final");
    public static final Set<String> STATUSES = Set.of("preparing", "applied", "interviewing", "done", "archived");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final JobGoalRepository goals;
    private final StudyPlanRepository plans;
    private final StudyPlanItemRepository planItems;
    private final PracticeSessionRepository sessions;
    private final PracticeScoreRepository scores;
    private final PracticeAnswerRepository answers;
    private final PracticeSessionService practiceSessions;

    public JobGoalService(JobGoalRepository goals, StudyPlanRepository plans, StudyPlanItemRepository planItems,
                          PracticeSessionRepository sessions, PracticeScoreRepository scores,
                          PracticeAnswerRepository answers, PracticeSessionService practiceSessions) {
        this.goals = goals;
        this.plans = plans;
        this.planItems = planItems;
        this.sessions = sessions;
        this.scores = scores;
        this.answers = answers;
        this.practiceSessions = practiceSessions;
    }

    @Transactional
    public JobGoalView create(String ownerId, JobGoalRequest request) {
        Instant now = Instant.now();
        JobGoalEntity entity = new JobGoalEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerId(ownerId);
        apply(entity, request);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return view(goals.save(entity));
    }

    @Transactional
    public JobGoalView update(String ownerId, UUID id, JobGoalRequest request) {
        JobGoalEntity entity = requireOwned(ownerId, id);
        apply(entity, request);
        entity.setUpdatedAt(Instant.now());
        return view(goals.save(entity));
    }

    @Transactional(readOnly = true)
    public List<JobGoalView> list(String ownerId) {
        return goals.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(this::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public JobGoalView get(String ownerId, UUID id) {
        return view(requireOwned(ownerId, id));
    }

    @Transactional(readOnly = true)
    public List<PracticeSessionView> sessions(String ownerId, UUID id) {
        requireOwned(ownerId, id);
        return practiceSessions.listByGoal(ownerId, id);
    }

    @Transactional
    public JobGoalView archive(String ownerId, UUID id) {
        JobGoalEntity entity = requireOwned(ownerId, id);
        entity.setStatus("archived");
        entity.setArchivedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return view(goals.save(entity));
    }

    /** 删除目标：练习保留但解绑，计划与条目级联删除。 */
    @Transactional
    public void delete(String ownerId, UUID id) {
        JobGoalEntity entity = requireOwned(ownerId, id);
        sessions.detachGoal(id);
        plans.deleteAllByGoalId(id);
        goals.delete(entity);
    }

    public JobGoalEntity requireOwned(String ownerId, UUID id) {
        return goals.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "目标不存在"));
    }

    /** 每日计划优先选"最近要面试的未归档目标"。 */
    @Transactional(readOnly = true)
    public JobGoalEntity preferredGoal(String ownerId) {
        return goals.findActiveForPlan(ownerId).stream().findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public GoalProgressView progress(String ownerId, UUID goalId) {
        List<PracticeSessionEntity> goalSessions =
                sessions.findAllByOwnerIdAndGoalIdOrderByCreatedAtDesc(ownerId, goalId);
        List<UUID> sessionIds = goalSessions.stream().map(PracticeSessionEntity::getId).toList();
        List<PracticeScoreEntity> scoreRows = sessionIds.isEmpty() ? List.of()
                : scores.findAllByOwnerIdAndSessionIdIn(ownerId, sessionIds);

        int answeredCount = sessionIds.isEmpty() ? 0 : (int) answers.countBySessionIdIn(sessionIds);
        java.util.OptionalDouble averageValue = scoreRows.stream()
                .map(PracticeScoreEntity::getOverallScore)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .average();
        Double average = averageValue.isPresent() ? Math.round(averageValue.getAsDouble() * 100.0) / 100.0 : null;
        Map<String, Double> dimensions = new LinkedHashMap<>();
        put(dimensions, "relevance", avg(scoreRows, PracticeScoreEntity::getRelevance));
        put(dimensions, "completeness", avg(scoreRows, PracticeScoreEntity::getCompleteness));
        put(dimensions, "professionalism", avg(scoreRows, PracticeScoreEntity::getProfessionalism));
        put(dimensions, "starStructure", avg(scoreRows, PracticeScoreEntity::getStarStructure));
        put(dimensions, "logic", avg(scoreRows, PracticeScoreEntity::getLogic));

        List<StudyPlanEntity> goalPlans = plans.findAllByOwnerIdAndGoalIdOrderByCreatedAtDesc(ownerId, goalId);
        List<StudyPlanItemEntity> items = goalPlans.isEmpty() ? List.of()
                : planItems.findAllByPlanIdIn(goalPlans.stream().map(StudyPlanEntity::getId).toList());
        int completed = (int) items.stream().filter(item -> "completed".equals(item.getStatus())).count();
        Instant lastPracticed = goalSessions.stream().map(PracticeSessionEntity::getCreatedAt)
                .filter(java.util.Objects::nonNull).max(Instant::compareTo).orElse(null);

        return new GoalProgressView(goalSessions.size(), answeredCount, average, dimensions,
                items.size(), completed, lastPracticed);
    }

    private JobGoalView view(JobGoalEntity entity) {
        Long days = entity.getInterviewAt() == null ? null
                : ChronoUnit.DAYS.between(LocalDate.now(ZONE),
                entity.getInterviewAt().atZone(ZONE).toLocalDate());
        return new JobGoalView(entity.getId(), entity.getCompany(), entity.getRole(), entity.getJobDescription(),
                entity.getResumeId(), entity.getStage(), entity.getInterviewAt(), entity.getStatus(),
                entity.getNotes(), entity.getCreatedAt(), entity.getUpdatedAt(), days,
                progress(entity.getOwnerId(), entity.getId()));
    }

    private static void apply(JobGoalEntity entity, JobGoalRequest request) {
        String stage = request.stage() == null || request.stage().isBlank() ? "technical" : request.stage();
        if (!STAGES.contains(stage)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知的面试轮次");
        String status = request.status() == null || request.status().isBlank() ? "preparing" : request.status();
        if (!STATUSES.contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知的目标状态");
        entity.setCompany(trimToNull(request.company()));
        entity.setRole(request.role().strip());
        entity.setJobDescription(trimToNull(request.jobDescription()));
        entity.setResumeId(request.resumeId());
        entity.setStage(stage);
        entity.setInterviewAt(request.interviewAt());
        entity.setStatus(status);
        entity.setNotes(trimToNull(request.notes()));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static Double avg(List<PracticeScoreEntity> rows,
                              java.util.function.Function<PracticeScoreEntity, BigDecimal> pick) {
        double value = rows.stream().map(pick).filter(java.util.Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue).average().orElse(Double.NaN);
        return Double.isNaN(value) ? null : Math.round(value * 100.0) / 100.0;
    }

    private static void put(Map<String, Double> target, String key, Double value) {
        if (value != null) target.put(key, value);
    }
}
