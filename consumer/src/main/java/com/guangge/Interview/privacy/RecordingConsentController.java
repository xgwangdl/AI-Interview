package com.guangge.Interview.privacy;

import com.guangge.Interview.practice.security.OwnerResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/consents")
public class RecordingConsentController {

    private final OwnerResolver owners;
    private final RecordingConsentService consents;

    public RecordingConsentController(OwnerResolver owners, RecordingConsentService consents) {
        this.owners = owners;
        this.consents = consents;
    }

    @GetMapping("/recording")
    public RecordingConsentView status(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return consents.status(owners.requireOwner(authorization));
    }

    @PostMapping("/recording")
    public RecordingConsentView accept(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody RecordingConsentRequest request) {
        return consents.accept(owners.requireOwner(authorization), request.version());
    }
}
