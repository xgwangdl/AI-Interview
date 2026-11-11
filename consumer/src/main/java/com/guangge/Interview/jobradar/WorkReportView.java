package com.guangge.Interview.jobradar;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record WorkReportView(
        String period,
        LocalDate from,
        LocalDate to,
        long jobsEvaluated,
        long practiceFromJd,
        long debriefs,
        long weakPointsOpened,
        long weakPointsClosed,
        int modelCalls,
        int minutesSaved,
        Map<String, Long> byKind,
        List<String> suggestions) { }
