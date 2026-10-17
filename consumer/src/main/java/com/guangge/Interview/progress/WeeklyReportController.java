package com.guangge.Interview.progress;

import com.guangge.Interview.practice.security.OwnerResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/progress")
public class WeeklyReportController {

    private final OwnerResolver owners;
    private final WeeklyReportService reports;

    public WeeklyReportController(OwnerResolver owners, WeeklyReportService reports) {
        this.owners = owners;
        this.reports = reports;
    }

    @GetMapping("/weekly")
    public WeeklyReportView weekly(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(required = false) String week) {
        return reports.report(owners.requireOwner(authorization),
                week == null || week.isBlank() ? null : LocalDate.parse(week));
    }
}
