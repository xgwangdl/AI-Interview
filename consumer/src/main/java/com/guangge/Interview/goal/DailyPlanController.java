package com.guangge.Interview.goal;

import com.guangge.Interview.practice.dto.PracticeSessionView;
import com.guangge.Interview.practice.security.OwnerResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 今日训练计划接口（Phase 1）。 */
@RestController
@RequestMapping("/api/v1")
public class DailyPlanController {

    private final OwnerResolver owners;
    private final DailyPlanService plans;

    public DailyPlanController(OwnerResolver owners, DailyPlanService plans) {
        this.owners = owners;
        this.plans = plans;
    }

    @GetMapping("/plans/today")
    public DailyPlanView today(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(required = false) UUID goalId) {
        return plans.today(owners.requireOwner(authorization), goalId);
    }

    @PatchMapping("/plans/items/{id}")
    public PlanItemView updateStatus(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @Valid @RequestBody PlanItemStatusRequest request) {
        return plans.setStatus(owners.requireOwner(authorization), id, request.status());
    }

    /** 从今日计划开始一题：后端直接建好一场单题练习，前端只需去设备检测。 */
    @PostMapping("/plans/items/{id}/start")
    public ResponseEntity<PracticeSessionView> start(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(plans.start(owners.requireOwner(authorization), id));
    }

    @GetMapping("/progress/streak")
    public DailyPlanView.StreakView streak(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return plans.streak(owners.requireOwner(authorization));
    }
}
