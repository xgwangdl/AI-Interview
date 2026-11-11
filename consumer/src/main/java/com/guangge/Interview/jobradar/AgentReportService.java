package com.guangge.Interview.jobradar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentReportService {
    private final JobAgentRunService runs;
    private final JobAgentRunRepository runRepository;
    private final TrainingWeakPointRepository weakPoints;
    private final JobLeadRepository leads;
    private final ZoneId zone;

    public AgentReportService(JobAgentRunService runs, JobAgentRunRepository runRepository,
                              TrainingWeakPointRepository weakPoints, JobLeadRepository leads,
                              @Value("${practice.job-radar.zone:Asia/Shanghai}") String zone) {
        this.runs = runs;
        this.runRepository = runRepository;
        this.weakPoints = weakPoints;
        this.leads = leads;
        this.zone = ZoneId.of(zone);
    }

    @Transactional(readOnly = true)
    public WorkReportView report(String ownerId, String period) {
        String safePeriod = "day".equals(period) ? "day" : "week";
        LocalDate today = LocalDate.now(zone);
        LocalDate fromDate = "day".equals(safePeriod) ? today : today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant from = fromDate.atStartOfDay(zone).toInstant();
        Instant now = Instant.now();

        List<JobAgentRunEntity> scoped = new ArrayList<>();
        for (String kind : List.of(JobAgentRunService.KIND_JOB_EVALUATE, JobAgentRunService.KIND_PRACTICE_FROM_JD,
                JobAgentRunService.KIND_DEBRIEF_PARSE, JobAgentRunService.KIND_WEAKPOINT_RECONCILE)) {
            scoped.addAll(runRepository.findAllByOwnerIdAndKindAndCreatedAtBetween(ownerId, kind, from, now));
        }
        Map<String, Long> byKind = new LinkedHashMap<>();
        for (JobAgentRunEntity run : scoped) {
            byKind.merge(run.getKind(), 1L, Long::sum);
        }
        long jobsEvaluated = succeeded(scoped, JobAgentRunService.KIND_JOB_EVALUATE);
        long practiceFromJd = succeeded(scoped, JobAgentRunService.KIND_PRACTICE_FROM_JD);
        long debriefs = succeeded(scoped, JobAgentRunService.KIND_DEBRIEF_PARSE);
        int modelCalls = scoped.stream().filter(run -> "succeeded".equals(run.getStatus()))
                .mapToInt(JobAgentRunEntity::getModelCalls).sum();
        int minutesSaved = scoped.stream().filter(run -> "succeeded".equals(run.getStatus()))
                .mapToInt(JobAgentRunEntity::getMinutesSaved).sum();
        long opened = weakPoints.countByOwnerIdAndCreatedAtBetween(ownerId, from, now);
        long closed = weakPoints.countByOwnerIdAndStatusAndClosedAtBetween(ownerId, "closed", from, now);

        List<String> suggestions = new ArrayList<>();
        weakPoints.findFirstByOwnerIdAndStatusOrderByCreatedAtAsc(ownerId, "open")
                .ifPresent(point -> suggestions.add("「" + point.getTopic() + "」尚未关闭，建议安排一场专项练习"));
        long shortlisted = leads.countByOwnerIdAndStatusIn(ownerId,
                List.of(JobLeadStatusMachine.SHORTLISTED, JobLeadStatusMachine.APPLIED));
        if (shortlisted > 0) suggestions.add("有 " + shortlisted + " 个岗位评估后仍在跟进，记得回填真实面试结果");
        if (debriefs == 0) suggestions.add("本周期还没有面试回填，面完当天记录更容易还原细节");

        return new WorkReportView(safePeriod, fromDate, today, jobsEvaluated, practiceFromJd, debriefs,
                opened, closed, modelCalls, minutesSaved, byKind, suggestions.stream().limit(3).toList());
    }

    public List<JobAgentRunView> recentRuns(String ownerId, int limit) {
        return runs.recent(ownerId, limit);
    }

    private static long succeeded(List<JobAgentRunEntity> runs, String kind) {
        return runs.stream().filter(run -> run.getKind().equals(kind)
                && "succeeded".equals(run.getStatus())).count();
    }
}
