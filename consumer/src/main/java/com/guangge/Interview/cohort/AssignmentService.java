package com.guangge.Interview.cohort;

import com.guangge.Interview.goal.StudyPlanEntity;
import com.guangge.Interview.goal.StudyPlanItemEntity;
import com.guangge.Interview.goal.StudyPlanItemRepository;
import com.guangge.Interview.goal.StudyPlanRepository;
import com.guangge.Interview.practice.entity.PracticeScoreEntity;
import com.guangge.Interview.practice.repository.PracticeScoreRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AssignmentService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final CohortService cohorts;
    private final CohortMemberRepository members;
    private final AssignmentRepository assignments;
    private final StudyPlanRepository plans;
    private final StudyPlanItemRepository planItems;
    private final PracticeScoreRepository scores;

    public AssignmentService(CohortService cohorts, CohortMemberRepository members,
                             AssignmentRepository assignments, StudyPlanRepository plans,
                             StudyPlanItemRepository planItems, PracticeScoreRepository scores) {
        this.cohorts = cohorts;
        this.members = members;
        this.assignments = assignments;
        this.plans = plans;
        this.planItems = planItems;
        this.scores = scores;
    }

    @Transactional
    public AssignmentView create(String ownerId, UUID cohortId, AssignmentRequest request) {
        cohorts.requireCoach(cohortId, ownerId);
        AssignmentEntity assignment = new AssignmentEntity();
        assignment.setId(UUID.randomUUID());
        assignment.setCohortId(cohortId);
        assignment.setTitle(request.title().strip());
        assignment.setDescription(request.description() == null ? null : request.description().strip());
        assignment.setDays(request.days());
        assignment.setItemsPerDay(request.itemsPerDay());
        assignment.setDueAt(request.dueAt());
        assignment.setCreatedBy(ownerId);
        assignment.setCreatedAt(Instant.now());
        assignments.save(assignment);
        for (CohortMemberEntity member : members.findAllByCohortIdOrderByJoinedAtAsc(cohortId)) {
            if ("learner".equals(member.getRole())) materialize(assignment, member.getOwnerId());
        }
        return view(assignment, ownerId);
    }

    /** 新成员加入后补上小组里已经存在的作业。 */
    @Transactional
    public void materializeAssignmentsForMember(UUID cohortId, String ownerId) {
        for (AssignmentEntity assignment : assignments.findAllByCohortIdOrderByCreatedAtDesc(cohortId)) {
            materialize(assignment, ownerId);
        }
    }

    @Transactional(readOnly = true)
    public List<AssignmentView> list(String ownerId, UUID cohortId) {
        cohorts.requireMember(cohortId, ownerId);
        return assignments.findAllByCohortIdOrderByCreatedAtDesc(cohortId).stream()
                .map(assignment -> view(assignment, ownerId))
                .toList();
    }

    @Transactional
    public AssignmentView get(String ownerId, UUID cohortId, UUID assignmentId) {
        cohorts.requireMember(cohortId, ownerId);
        AssignmentEntity assignment = assignments.findById(assignmentId)
                .filter(item -> item.getCohortId().equals(cohortId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "作业不存在"));
        return view(assignment, ownerId);
    }

    @Transactional
    public AssignmentView update(String ownerId, UUID cohortId, UUID assignmentId, AssignmentRequest request) {
        cohorts.requireCoach(cohortId, ownerId);
        AssignmentEntity assignment = assignments.findById(assignmentId)
                .filter(item -> item.getCohortId().equals(cohortId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "作业不存在"));

        boolean structureChanged = assignment.getDays() != request.days()
                || assignment.getItemsPerDay() != request.itemsPerDay()
                || !java.util.Objects.equals(assignment.getDueAt(), request.dueAt());
        if (structureChanged && hasLearningProgress(assignmentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "已有学员开始或完成作业，不能修改天数、每天题目数或截止时间");
        }

        assignment.setTitle(request.title().strip());
        assignment.setDescription(request.description() == null ? null : request.description().strip());
        assignment.setDays(request.days());
        assignment.setItemsPerDay(request.itemsPerDay());
        assignment.setDueAt(request.dueAt());
        assignments.save(assignment);

        if (structureChanged) {
            rebuild(assignment);
        }
        return view(assignment, ownerId);
    }

    private boolean hasLearningProgress(UUID assignmentId) {
        return planItems.findAllByAssignmentId(assignmentId).stream()
                .anyMatch(item -> "completed".equals(item.getStatus()));
    }

    /** 尚未开始作答时，作业结构可以安全重建；已有进度会被上面的校验阻止。 */
    private void rebuild(AssignmentEntity assignment) {
        List<StudyPlanItemEntity> existing = planItems.findAllByAssignmentId(assignment.getId());
        Set<UUID> planIds = new LinkedHashSet<>();
        existing.forEach(item -> planIds.add(item.getPlanId()));
        if (!existing.isEmpty()) planItems.deleteAll(existing);
        planIds.forEach(planId -> {
            if (planItems.countByPlanId(planId) == 0) plans.deleteById(planId);
        });
        for (CohortMemberEntity member : members.findAllByCohortIdOrderByJoinedAtAsc(assignment.getCohortId())) {
            if ("learner".equals(member.getRole())) materialize(assignment, member.getOwnerId());
        }
    }

    private void materialize(AssignmentEntity assignment, String ownerId) {
        if (planItems.existsByAssignmentIdAndOwnerId(assignment.getId(), ownerId)) return;
        LocalDate start = LocalDate.now(ZONE);
        LocalDate end = assignment.getDueAt() == null ? start.plusDays(assignment.getDays() - 1L)
                : assignment.getDueAt().atZone(ZONE).toLocalDate();
        StudyPlanEntity plan = new StudyPlanEntity();
        plan.setId(UUID.randomUUID());
        plan.setOwnerId(ownerId);
        plan.setGoalId(null);
        plan.setStartDate(start);
        plan.setEndDate(end.isBefore(start) ? start : end);
        plan.setStatus("active");
        plan.setCreatedAt(Instant.now());
        plans.save(plan);
        List<StudyPlanItemEntity> created = new ArrayList<>();
        int position = 1;
        for (int day = 0; day < assignment.getDays(); day++) {
            for (int item = 0; item < assignment.getItemsPerDay(); item++) {
                StudyPlanItemEntity row = new StudyPlanItemEntity();
                row.setId(UUID.randomUUID());
                row.setPlanId(plan.getId());
                row.setOwnerId(ownerId);
                row.setPlanDate(start.plusDays(day));
                row.setKind("assignment");
                row.setAssignmentId(assignment.getId());
                row.setTitle(assignment.getTitle() + " · 第 " + (day + 1) + " 天 · " + (item + 1) + "/"
                        + assignment.getItemsPerDay());
                row.setEstimatedMinutes(5);
                row.setStatus("pending");
                row.setPosition(position++);
                row.setCreatedAt(Instant.now());
                created.add(row);
            }
        }
        planItems.saveAll(created);
    }

    private AssignmentView view(AssignmentEntity assignment, String currentOwner) {
        // 作业看板只列学员：助教自己不是被布置作业的人，混进去会出现 0/0 的空行
        List<CohortMemberEntity> memberList = members.findAllByCohortIdOrderByJoinedAtAsc(assignment.getCohortId())
                .stream().filter(member -> "learner".equals(member.getRole())).toList();
        List<AssignmentView.MemberProgress> progress = memberList.stream()
                .map(member -> {
                    long total = planItems.countByAssignmentIdAndOwnerId(assignment.getId(), member.getOwnerId());
                    long completed = planItems.countByAssignmentIdAndOwnerIdAndStatus(
                            assignment.getId(), member.getOwnerId(), "completed");
                    double percent = total == 0 ? 0 : Math.round(completed * 1000.0 / total) / 10.0;
                    return new AssignmentView.MemberProgress(member.getOwnerId(), member.getRole(),
                            member.isShareScores(), (int) completed, (int) total, percent,
                            member.isShareScores() ? averageScore(member.getOwnerId()) : null);
                }).toList();
        int completedMembers = (int) progress.stream()
                .filter(row -> row.total() > 0 && row.completed() >= row.total()).count();
        AssignmentView.MemberProgress mine = progress.stream()
                .filter(row -> row.owner().equals(currentOwner)).findFirst().orElse(null);
        return new AssignmentView(assignment.getId(), assignment.getCohortId(), assignment.getTitle(),
                assignment.getDescription(), assignment.getDays(), assignment.getItemsPerDay(),
                assignment.getDueAt(), assignment.getCreatedAt(), progress.size(), completedMembers,
                mine == null ? 0 : mine.total(), mine == null ? 0 : mine.completed(), progress);
    }

    private Double averageScore(String ownerId) {
        java.util.OptionalDouble value = scores.findScoredQuestions(ownerId).stream()
                .map(PracticeScoreEntity::getOverallScore)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue).average();
        return value.isPresent() ? Math.round(value.getAsDouble() * 10) / 10.0 : null;
    }
}
