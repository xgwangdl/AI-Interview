package com.guangge.Interview.jobradar;

import java.util.List;
import java.util.Map;

public record JobLeadPageView(
        List<JobLeadSummaryView> items,
        long total,
        int offset,
        int limit,
        Map<String, Long> statusCounts) { }
