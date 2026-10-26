package com.guangge.Interview.cohort;

import com.guangge.Interview.practice.security.OwnerResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cohorts")
public class CohortController {

    private final OwnerResolver owners;
    private final CohortService cohorts;
    private final AssignmentService assignments;
    private final CohortHelpService help;
    private final PeerReviewService peerReviews;

    public CohortController(OwnerResolver owners, CohortService cohorts, AssignmentService assignments,
                            CohortHelpService help, PeerReviewService peerReviews) {
        this.owners = owners;
        this.cohorts = cohorts;
        this.assignments = assignments;
        this.help = help;
        this.peerReviews = peerReviews;
    }

    @PostMapping
    public ResponseEntity<CohortView> create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody CohortRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cohorts.create(owners.requireOwner(authorization), request));
    }

    @PostMapping("/join")
    public CohortView join(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody CohortJoinRequest request) {
        String owner = owners.requireOwner(authorization);
        CohortView cohort = cohorts.join(owner, request.inviteCode());
        assignments.materializeAssignmentsForMember(cohort.id(), owner);
        return cohort;
    }

    @GetMapping
    public List<CohortView> list(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return cohorts.list(owners.requireOwner(authorization));
    }

    @GetMapping("/{id}")
    public CohortView get(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return cohorts.get(owners.requireOwner(authorization), id);
    }

    @PutMapping("/{id}/members/me/score-sharing")
    public ResponseEntity<Void> shareScores(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @RequestBody ScoreSharingRequest request) {
        cohorts.setScoreSharing(owners.requireOwner(authorization), id, request.shareScores());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<Void> archive(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        cohorts.archive(owners.requireOwner(authorization), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/assignments")
    public List<AssignmentView> assignments(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return assignments.list(owners.requireOwner(authorization), id);
    }

    @PostMapping("/{id}/assignments")
    public ResponseEntity<AssignmentView> createAssignment(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @Valid @RequestBody AssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assignments.create(owners.requireOwner(authorization), id, request));
    }

    @GetMapping("/{id}/assignments/{assignmentId}")
    public AssignmentView assignment(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @PathVariable UUID assignmentId) {
        return assignments.get(owners.requireOwner(authorization), id, assignmentId);
    }

    @PutMapping("/{id}/assignments/{assignmentId}")
    public AssignmentView updateAssignment(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody AssignmentRequest request) {
        return assignments.update(owners.requireOwner(authorization), id, assignmentId, request);
    }

    @PostMapping("/{id}/help")
    public ResponseEntity<CohortHelpView> requestHelp(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @Valid @RequestBody CohortHelpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(help.create(owners.requireOwner(authorization), id, request));
    }

    @GetMapping("/{id}/help")
    public List<CohortHelpView> help(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @RequestParam(required = false) String status) {
        return help.list(owners.requireOwner(authorization), id, status);
    }

    @PostMapping("/{id}/help/{helpId}/feedback")
    public CohortHelpView feedback(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @PathVariable UUID helpId,
            @Valid @RequestBody CohortHelpFeedbackRequest request) {
        return help.feedback(owners.requireOwner(authorization), id, helpId, request.text());
    }

    @PutMapping("/{id}/members/me/peer-review")
    public ResponseEntity<Void> peerReviewOptIn(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @RequestBody PeerReviewOptInRequest request) {
        peerReviews.optIn(owners.requireOwner(authorization), id, request.enabled());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/peer-reviews/tasks")
    public List<PeerReviewTaskView> peerReviewTasks(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "5") int limit) {
        return peerReviews.tasks(owners.requireOwner(authorization), id, limit);
    }

    @PostMapping("/{id}/peer-reviews")
    public ResponseEntity<Void> submitPeerReview(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @Valid @RequestBody PeerReviewRequest request) {
        peerReviews.submit(owners.requireOwner(authorization), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{id}/peer-reviews/received")
    public List<PeerReviewReceivedView> receivedPeerReviews(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return peerReviews.received(owners.requireOwner(authorization), id);
    }
}
