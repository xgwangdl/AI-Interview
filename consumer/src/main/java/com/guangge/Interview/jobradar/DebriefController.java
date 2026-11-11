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

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/interview-debriefs")
public class DebriefController {
    private final OwnerResolver owners;
    private final DebriefService debriefs;

    public DebriefController(OwnerResolver owners, DebriefService debriefs) {
        this.owners = owners;
        this.debriefs = debriefs;
    }

    @PostMapping
    public ResponseEntity<DebriefCreateResponse> create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody DebriefCreateRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(debriefs.create(owners.requireOwner(authorization), request));
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        return debriefs.list(owners.requireOwner(authorization), offset, limit);
    }

    @GetMapping("/{id}")
    public DebriefView get(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return debriefs.get(owners.requireOwner(authorization), id);
    }

    @PatchMapping("/{id}/questions")
    public DebriefView updateQuestions(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @Valid @RequestBody DebriefQuestionUpdateRequest request) {
        return debriefs.updateQuestions(owners.requireOwner(authorization), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        debriefs.delete(owners.requireOwner(authorization), id);
        return ResponseEntity.noContent().build();
    }
}
