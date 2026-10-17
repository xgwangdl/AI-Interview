package com.guangge.Interview.goal;

import com.guangge.Interview.practice.dto.PracticeSessionView;
import com.guangge.Interview.practice.security.OwnerResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** 求职目标接口（Phase 1）。 */
@RestController
@RequestMapping("/api/v1/goals")
public class JobGoalController {

    private final OwnerResolver owners;
    private final JobGoalService goals;

    public JobGoalController(OwnerResolver owners, JobGoalService goals) {
        this.owners = owners;
        this.goals = goals;
    }

    @GetMapping
    public List<JobGoalView> list(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return goals.list(owners.requireOwner(authorization));
    }

    @PostMapping
    public ResponseEntity<JobGoalView> create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Validated @RequestBody JobGoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(goals.create(owners.requireOwner(authorization), request));
    }

    @GetMapping("/{id}")
    public JobGoalView get(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return goals.get(owners.requireOwner(authorization), id);
    }

    @PutMapping("/{id}")
    public JobGoalView update(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @Valid @RequestBody JobGoalRequest request) {
        return goals.update(owners.requireOwner(authorization), id, request);
    }

    @PostMapping("/{id}/archive")
    public JobGoalView archive(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return goals.archive(owners.requireOwner(authorization), id);
    }

    @GetMapping("/{id}/sessions")
    public List<PracticeSessionView> sessions(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return goals.sessions(owners.requireOwner(authorization), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        goals.delete(owners.requireOwner(authorization), id);
        return ResponseEntity.noContent().build();
    }
}
