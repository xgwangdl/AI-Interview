package com.guangge.Interview.practice.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.guangge.Interview.auth.Sign;
import com.guangge.Interview.practice.dto.PracticeAnswerRequest;
import com.guangge.Interview.practice.dto.PracticeAnswerView;
import com.guangge.Interview.practice.dto.PracticeSessionRequest;
import com.guangge.Interview.practice.dto.PracticeSessionView;
import com.guangge.Interview.practice.service.PracticeSessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
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
    private final String tokenSecret;

    public PracticeSessionController(
            PracticeSessionService practiceSessions,
            @Value("${auth.token.secret}") String tokenSecret) {
        this.practiceSessions = practiceSessions;
        this.tokenSecret = tokenSecret;
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

    @GetMapping("/practice-sessions/{id}/report")
    public PracticeSessionView report(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return practiceSessions.get(owner(authorization), id);
    }

    @PostMapping("/practice-sessions/{id}/complete")
    public PracticeSessionView complete(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return practiceSessions.complete(owner(authorization), id);
    }

    @GetMapping("/answers/{answerId}/media")
    public ResponseEntity<Resource> getMedia(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID answerId) {
        PracticeSessionService.MediaFile media = practiceSessions.getAnswerMedia(owner(authorization), answerId);
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
                .body(new FileSystemResource(media.path()));
    }

    @DeleteMapping("/practice-sessions/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) throws IOException {
        practiceSessions.delete(owner(authorization), id);
        return ResponseEntity.noContent().build();
    }

    private String owner(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        try {
            DecodedJWT token = Sign.verifyToken(authorization.substring(7), tokenSecret);
            String username = token.getClaim("userName").asString();
            if (username == null || username.isBlank()) throw new IllegalArgumentException("Missing user name claim");
            return username;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }
}
