package com.guangge.Interview.goal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DailyPlanView(
        LocalDate date,
        UUID goalId,
        String goalTitle,
        String role,
        String company,
        int completedCount,
        int totalCount,
        StreakView streak,
        List<PlanItemView> items) {

    public record StreakView(int current, int longest) { }
}
