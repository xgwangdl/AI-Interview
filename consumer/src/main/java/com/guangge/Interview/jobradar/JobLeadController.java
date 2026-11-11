package com.guangge.Interview.jobradar;

import com.guangge.Interview.practice.security.OwnerResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

@RestController
@RequestMapping("/api/v1/job-leads")
public class JobLeadController {
    private final OwnerResolver owners;
    private final JobLeadService leads;
    private final JobPracticeService practice;

    public JobLeadController(OwnerResolver owners, JobLeadService leads, JobPracticeService practice) {
        this.owners = owners;
        this.leads = leads;
        this.practice = practice;
    }

    @PostMapping
    public ResponseEntity<JobLeadCreateResponse> create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody JobLeadCreateRequest request) {
        JobLeadCreateResponse response = leads.create(owners.requireOwner(authorization), request);
        return ResponseEntity.status(response.duplicated() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public JobLeadPageView list(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        return leads.list(owners.requireOwner(authorization), status, role, offset, limit);
    }

    @GetMapping("/{id}")
    public JobLeadView get(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return leads.get(owners.requireOwner(authorization), id);
    }

    @PatchMapping("/{id}")
    public JobLeadView patch(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @Valid @RequestBody JobLeadPatchRequest request) {
        return leads.patch(owners.requireOwner(authorization), id, request);
    }

    @PostMapping("/{id}/evaluate")
    public JobEvaluationRunResponse evaluate(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @RequestBody(required = false) JobLeadEvaluateRequest request) {
        return leads.evaluate(owners.requireOwner(authorization), id, request != null && request.force());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        leads.delete(owners.requireOwner(authorization), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/practice")
    public JobPracticeResponse practice(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @Valid @RequestBody JobPracticeRequest request) {
        return practice.create(owners.requireOwner(authorization), id, request);
    }
}
