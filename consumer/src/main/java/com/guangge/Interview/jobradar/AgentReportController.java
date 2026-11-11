package com.guangge.Interview.jobradar;

import com.guangge.Interview.practice.security.OwnerResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentReportController {
    private final OwnerResolver owners;
    private final AgentReportService reports;

    public AgentReportController(OwnerResolver owners, AgentReportService reports) {
        this.owners = owners;
        this.reports = reports;
    }

    @GetMapping("/work-report")
    public WorkReportView report(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(defaultValue = "week") String period) {
        return reports.report(owners.requireOwner(authorization), period);
    }

    @GetMapping("/runs")
    public List<JobAgentRunView> runs(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(defaultValue = "20") int limit) {
        return reports.recentRuns(owners.requireOwner(authorization), Math.max(1, Math.min(20, limit)));
    }
}
