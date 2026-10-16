package com.guangge.Interview.practice.controller;

import com.guangge.Interview.analysis.AnalysisJobService;
import com.guangge.Interview.practice.security.OwnerResolver;
import com.guangge.Interview.practice.dto.PracticeAnswerRequest;
import com.guangge.Interview.practice.dto.PracticeAnswerView;
import com.guangge.Interview.practice.dto.PracticeFollowUpRequest;
import com.guangge.Interview.practice.dto.PracticeFollowUpView;
import com.guangge.Interview.practice.dto.MediaSignedUrlView;
import com.guangge.Interview.practice.dto.PracticeReportView;
import com.guangge.Interview.practice.dto.PracticeSessionRequest;
import com.guangge.Interview.practice.dto.PracticeSessionView;
import com.guangge.Interview.practice.media.MediaSignedUrlService;
import com.guangge.Interview.practice.service.PracticeReportService;
import com.guangge.Interview.practice.service.PracticeSessionService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1")
public class PracticeSessionController {
    private final PracticeSessionService practiceSessions;
    private final PracticeReportService practiceReports;
    private final AnalysisJobService analysisJobs;
    private final MediaSignedUrlService signedUrls;
    private final OwnerResolver owners;
    private final com.guangge.Interview.practice.followup.PracticeFollowUpService followUps;

    public PracticeSessionController(
            PracticeSessionService practiceSessions,
            PracticeReportService practiceReports,
            AnalysisJobService analysisJobs,
            MediaSignedUrlService signedUrls,
            OwnerResolver owners,
            com.guangge.Interview.practice.followup.PracticeFollowUpService followUps) {
        this.practiceSessions = practiceSessions;
        this.practiceReports = practiceReports;
        this.analysisJobs = analysisJobs;
        this.signedUrls = signedUrls;
        this.owners = owners;
        this.followUps = followUps;
    }

    @PostMapping("/practice-sessions")
    public ResponseEntity<PracticeSessionView> create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Validated @RequestBody PracticeSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(practiceSessions.create(owner(authorization), request));
    }

    @GetMapping("/practice-sessions")
    public List<PracticeSessionView> list(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return practiceSessions.list(owner(authorization));
    }

    @GetMapping("/practice-sessions/{id}")
    public PracticeSessionView get(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return practiceSessions.get(owner(authorization), id);
    }

    @PostMapping("/practice-sessions/{id}/start")
    public PracticeSessionView start(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return practiceSessions.start(owner(authorization), id);
    }

    @PostMapping("/practice-sessions/{id}/answers")
    public ResponseEntity<PracticeAnswerView> saveAnswer(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @Validated @RequestBody PracticeAnswerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(practiceSessions.saveAnswer(owner(authorization), id, request));
    }

    @PostMapping(value = "/answers/{answerId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PracticeAnswerView uploadMedia(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID answerId,
            @RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video file is empty");
        try (InputStream content = file.getInputStream()) {
            return practiceSessions.storeAnswerMedia(owner(authorization), answerId, file.getContentType(),
                    file.getSize(), content);
        }
    }

    @GetMapping("/practice-sessions/{id}/status")
    public PracticeSessionView status(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return practiceSessions.get(owner(authorization), id);
    }

    /**
     * 面试官追问：就刚刚这道题的回答，问一句（也可能判断不需要追问）。
     *
     * 同步接口：内部要做一次语音转写 + 一次模型判断，所以前端要显示"正在考虑是否追问"。
     * 判断为不追问时返回 `question: null`，前端照常进入下一题。
     */
    @PostMapping("/practice-sessions/{id}/follow-ups")
    public PracticeFollowUpView followUp(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @Valid @RequestBody PracticeFollowUpRequest request) {
        return followUps.followUp(owner(authorization), id, request.answerId());
    }

    @GetMapping("/practice-sessions/{id}/report")
    public PracticeReportView report(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return practiceReports.report(owner(authorization), id);
    }

    @PostMapping("/practice-sessions/{id}/complete")
    public PracticeSessionView complete(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        PracticeSessionView completed = practiceSessions.complete(owner(authorization), id);
        analysisJobs.enqueue(id);
        return completed;
    }

    /** 单题录制完成即开始分析，不必等整场练习结束。 */
    @PostMapping("/answers/{answerId}/complete")
    public PracticeAnswerView completeAnswer(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID answerId) {
        PracticeAnswerView answer = practiceSessions.completeAnswer(owner(authorization), answerId);
        analysisJobs.enqueueAnswer(answerId);
        return answer;
    }

    /** 分析失败后由用户手动重试，返回重新排队后的报告。 */
    @PostMapping("/practice-sessions/{id}/analysis/retry")
    public PracticeReportView retryAnalysis(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        String ownerId = owner(authorization);
        practiceSessions.get(ownerId, id);
        analysisJobs.retryFailed(id);
        return practiceReports.report(ownerId, id);
    }

    @GetMapping("/answers/{answerId}/media")
    public ResponseEntity<Resource> getMedia(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID answerId) {
        return mediaResponse(practiceSessions.getAnswerMedia(owner(authorization), answerId));
    }

    @GetMapping("/media/{answerId}/signed-url")
    public MediaSignedUrlView signedUrl(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID answerId) {
        // Ownership is enforced before a signed URL is handed out.
        practiceSessions.getAnswerMedia(owner(authorization), answerId);
        return signedUrls.issue(answerId);
    }

    @GetMapping("/media/{answerId}")
    public ResponseEntity<Resource> signedMedia(
            @PathVariable UUID answerId,
            @RequestParam(name = "expires", required = false) Long expires,
            @RequestParam(name = "signature", required = false) String signature) {
        signedUrls.verify(answerId, expires, signature);
        return mediaResponse(practiceSessions.getAnswerMedia(answerId));
    }

    private static ResponseEntity<Resource> mediaResponse(PracticeSessionService.MediaFile media) {
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (media.contentType() != null) {
            try {
                mediaType = MediaType.parseMediaType(media.contentType());
            } catch (IllegalArgumentException ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(mediaType)
                .body(media.resource());
    }

    @DeleteMapping("/practice-sessions/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) throws IOException {
        practiceSessions.delete(owner(authorization), id);
        return ResponseEntity.noContent().build();
    }

    private String owner(String authorization) {
        return owners.requireOwner(authorization);
    }
}
