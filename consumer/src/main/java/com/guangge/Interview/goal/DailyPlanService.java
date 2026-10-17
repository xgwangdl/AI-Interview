package com.guangge.Interview.goal;

import com.guangge.Interview.practice.archive.PracticeArchiveService;
import com.guangge.Interview.practice.dto.PracticeQuestion;
import com.guangge.Interview.practice.dto.PracticeSessionRequest;
import com.guangge.Interview.practice.dto.PracticeSessionView;
import com.guangge.Interview.practice.mistakes.MistakeBookService;
import com.guangge.Interview.practice.service.PracticeSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 每日三题。
 *
 * 先用可解释规则选题，不额外调用模型：1 道薄弱项 + 1 道错题（没有就岗位高频）+ 1 道岗位高频。
 * 题目内容在"开始"时才生成，保证打开首页很快。
 */
@Service
public class DailyPlanService {

    public static final int ITEMS_PER_DAY = 3;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Map<String, String> DIMENSION_LABELS = Map.of(
            "relevance", "内容相关性",
            "completeness", "回答完整度",
            "professionalism", "专业程度",
            "starStructure", "STAR 结构",
            "logic", "回答逻辑");

    private final JobGoalService goals;
    private final StudyPlanRepository plans;
    private final StudyPlanItemRepository items;
    private final PracticeSessionService practiceSessions;
    private final PracticeArchiveService archive;
    private final MistakeBookService mistakes;

    public DailyPlanService(JobGoalService goals, StudyPlanRepository plans, StudyPlanItemRepository items,
                            PracticeSessionService practiceSessions, PracticeArchiveService archive,
                            MistakeBookService mistakes) {
        this.goals = goals;
        this.plans = plans;
        this.items = items;
        this.practiceSessions = practiceSessions;
        this.archive = archive;
        this.mistakes = mistakes;
    }

    @Transactional
    public DailyPlanView today(String ownerId, UUID goalId) {
        LocalDate today = LocalDate.now(ZONE);
        JobGoalEntity goal = resolveGoal(ownerId, goalId);
        StudyPlanEntity plan = activePlan(ownerId, goal == null ? null : goal.getId(), today);
        if (plan == null) {
            plan = createPlan(ownerId, goal == null ? null : goal.getId(), today);
        }
        List<StudyPlanItemEntity> todayItems =
                items.findAllByPlanIdAndPlanDateOrderByPositionAsc(plan.getId(), today);
        if (todayItems.isEmpty()) {
            todayItems = generateItems(ownerId, goal, plan, today);
        }
        return view(ownerId, today, goal, todayItems);
    }

    @Transactional
    public PlanItemView setStatus(String ownerId, UUID itemId, String status) {
        String value = status == null ? "" : status.strip().toLowerCase();
        if (!List.of("pending", "completed", "skipped").contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知的计划状态");
        }
        StudyPlanItemEntity item = requireItem(ownerId, itemId);
        item.setStatus(value);
        item.setCompletedAt("completed".equals(value) ? Instant.now() : null);
        return view(items.save(item));
    }

    /**
     * 开始计划项：创建一场只有一道题的练习。
     *
     * 错题项复用错题本的原题；其它项用模板生成一道题（不调用模型），
     * 这样"从首页到开口"不会被模型延迟卡住。
     */
    @Transactional
    public PracticeSessionView start(String ownerId, UUID itemId) {
        StudyPlanItemEntity item = requireItem(ownerId, itemId);
        StudyPlanEntity plan = plans.findById(item.getPlanId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "计划不存在"));
        if (item.getSessionId() != null) {
            return practiceSessions.get(ownerId, item.getSessionId());
        }
        JobGoalEntity goal = plan.getGoalId() == null ? null : goals.requireOwned(ownerId, plan.getGoalId());
        PracticeSessionView session;
        if ("mistake".equals(item.getKind()) && item.getQuestionRef() != null) {
            session = mistakes.retry(ownerId, item.getQuestionRef());
            if (goal != null) session = practiceSessions.linkGoal(ownerId, session.id(), goal.getId());
        } else {
            session = practiceSessions.create(ownerId, requestFor(goal, item));
        }
        item.setSessionId(session.id());
        item.setStatus("in_progress");
        items.save(item);
        return session;
    }

    /** 练习结束时把对应计划项标成完成（由 Controller 调用）。 */
    @Transactional
    public void sessionCompleted(UUID sessionId) {
        items.findFirstBySessionId(sessionId).ifPresent(item -> {
            item.setStatus("completed");
            item.setCompletedAt(Instant.now());
            items.save(item);
        });
    }

    @Transactional(readOnly = true)
    public DailyPlanView.StreakView streak(String ownerId) {
        LocalDate today = LocalDate.now(ZONE);
        List<StudyPlanItemEntity> recent = items.findAllByOwnerIdAndPlanDateBetweenOrderByPlanDateDescPositionAsc(
                ownerId, today.minusDays(60), today);
        Map<LocalDate, Long> completedByDay = new LinkedHashMap<>();
        for (StudyPlanItemEntity item : recent) {
            if (!"completed".equals(item.getStatus())) continue;
            completedByDay.merge(item.getPlanDate(), 1L, Long::sum);
        }
        int current = 0;
        LocalDate cursor = completedByDay.getOrDefault(today, 0L) >= 2 ? today : today.minusDays(1);
        while (completedByDay.getOrDefault(cursor, 0L) >= 2) {
            current++;
            cursor = cursor.minusDays(1);
        }
        int longest = 0;
        int run = 0;
        LocalDate previous = null;
        List<LocalDate> days = new ArrayList<>(completedByDay.keySet().stream()
                .filter(day -> completedByDay.get(day) >= 2)
                .sorted(Comparator.naturalOrder()).toList());
        for (LocalDate day : days) {
            run = previous != null && previous.plusDays(1).equals(day) ? run + 1 : 1;
            longest = Math.max(longest, run);
            previous = day;
        }
        return new DailyPlanView.StreakView(current, longest);
    }

    private JobGoalEntity resolveGoal(String ownerId, UUID goalId) {
        if (goalId != null) return goals.requireOwned(ownerId, goalId);
        return goals.preferredGoal(ownerId);
    }

    private StudyPlanEntity activePlan(String ownerId, UUID goalId, LocalDate today) {
        return plans.findAllByOwnerIdAndStatus(ownerId, "active").stream()
                .filter(plan -> Objects.equals(plan.getGoalId(), goalId))
                .filter(plan -> !today.isBefore(plan.getStartDate()) && !today.isAfter(plan.getEndDate()))
                .max(Comparator.comparing(StudyPlanEntity::getCreatedAt))
                .orElse(null);
    }

    private StudyPlanEntity createPlan(String ownerId, UUID goalId, LocalDate today) {
        StudyPlanEntity plan = new StudyPlanEntity();
        plan.setId(UUID.randomUUID());
        plan.setOwnerId(ownerId);
        plan.setGoalId(goalId);
        plan.setStartDate(today);
        plan.setEndDate(today.plusDays(6));
        plan.setStatus("active");
        plan.setCreatedAt(Instant.now());
        return plans.save(plan);
    }

    private List<StudyPlanItemEntity> generateItems(String ownerId, JobGoalEntity goal,
                                                    StudyPlanEntity plan, LocalDate today) {
        List<StudyPlanItemEntity> created = new ArrayList<>();
        String weak = archive.summary(ownerId, 5).weakest().stream()
                .map(item -> item.key()).findFirst().orElse("starStructure");
        created.add(newItem(ownerId, plan, today, 1, "weak_dimension", weak,
                "薄弱项：" + DIMENSION_LABELS.getOrDefault(weak, weak), 5));

        var book = mistakes.summary(ownerId, null, 0, 1);
        if (!book.mistakes().isEmpty()) {
            var mistake = book.mistakes().get(0);
            String title = mistake.text().length() > 36 ? mistake.text().substring(0, 36) + "…" : mistake.text();
            created.add(newItem(ownerId, plan, today, 2, "mistake", mistake.questionId(),
                    "错题重练：" + title, 5));
        } else {
            created.add(newItem(ownerId, plan, today, 2, "frequent", null,
                    "岗位高频：" + role(goal) + " 项目追问", 5));
        }
        created.add(newItem(ownerId, plan, today, 3, "frequent", null,
                "岗位高频：" + role(goal) + " 行为面", 5));
        return items.saveAll(created);
    }

    private StudyPlanItemEntity newItem(String ownerId, StudyPlanEntity plan, LocalDate date, int position,
                                        String kind, String questionRef, String title, int minutes) {
        StudyPlanItemEntity item = new StudyPlanItemEntity();
        item.setId(UUID.randomUUID());
        item.setPlanId(plan.getId());
        item.setOwnerId(ownerId);
        item.setPlanDate(date);
        item.setKind(kind);
        item.setQuestionRef(questionRef);
        item.setTitle(title);
        item.setEstimatedMinutes(minutes);
        item.setStatus("pending");
        item.setPosition(position);
        item.setCreatedAt(Instant.now());
        return item;
    }

    private PracticeSessionRequest requestFor(JobGoalEntity goal, StudyPlanItemEntity item) {
        String role = role(goal);
        String text = questionText(item, role);
        return new PracticeSessionRequest(
                role,
                goal == null ? null : goal.getCompany(),
                goal == null ? null : goal.getJobDescription(),
                null,
                goal == null ? null : goal.getId(),
                "behavioral",
                "standard",
                "balanced",
                1,
                4,
                true,
                List.of(new PracticeQuestion("plan-" + item.getId(), text, category(item))));
    }

    private static String role(JobGoalEntity goal) {
        return goal == null || goal.getRole() == null || goal.getRole().isBlank() ? "通用岗位" : goal.getRole();
    }

    private static String category(StudyPlanItemEntity item) {
        return switch (item.getKind()) {
            case "weak_dimension" -> "重点提升";
            case "mistake" -> "错题重练";
            default -> "岗位高频";
        };
    }

    private static String questionText(StudyPlanItemEntity item, String role) {
        if ("weak_dimension".equals(item.getKind())) {
            return switch (item.getQuestionRef() == null ? "" : item.getQuestionRef()) {
                case "logic" -> "请用“结论—理由—例子”的结构，说明你解决过的一个复杂问题。";
                case "completeness" -> "请完整讲一个项目：目标、你的具体动作、结果和一个复盘。";
                case "relevance" -> "请结合目标岗位，讲一个最能证明你匹配度的项目经历。";
                case "professionalism" -> "请讲一个你做过的最有技术含量的工作，说明关键取舍。";
                default -> "请用 STAR 结构讲一个你主导或深度参与的项目：背景、任务、你的行动和结果。";
            };
        }
        if ("frequent".equals(item.getKind())) {
            return "请结合 " + role + " 的岗位要求，讲一个最能体现你能力的项目，并说明结果。";
        }
        return "请讲一个你最近解决的实际问题。";
    }

    private StudyPlanItemEntity requireItem(String ownerId, UUID itemId) {
        return items.findByIdAndOwnerId(itemId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "计划项不存在"));
    }

    private static PlanItemView view(StudyPlanItemEntity item) {
        return new PlanItemView(item.getId(), item.getKind(), item.getTitle(), item.getEstimatedMinutes(),
                item.getStatus(), item.getSessionId(), item.getPosition());
    }

    private DailyPlanView view(String ownerId, LocalDate today, JobGoalEntity goal,
                               List<StudyPlanItemEntity> todayItems) {
        int completed = (int) todayItems.stream().filter(item -> "completed".equals(item.getStatus())).count();
        return new DailyPlanView(today,
                goal == null ? null : goal.getId(),
                goal == null ? "通用训练" : (goal.getCompany() == null ? "" : goal.getCompany() + " · ") + goal.getRole(),
                role(goal),
                goal == null ? null : goal.getCompany(),
                completed,
                todayItems.size(),
                streak(ownerId),
                todayItems.stream().map(DailyPlanService::view).toList());
    }
}
