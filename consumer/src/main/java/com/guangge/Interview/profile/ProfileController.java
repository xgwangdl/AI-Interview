package com.guangge.Interview.profile;

import com.guangge.Interview.practice.security.OwnerResolver;
import com.guangge.Interview.services.CandidatesService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/me")
public class ProfileController {

    private final OwnerResolver owners;
    private final ProfileService profiles;
    private final CandidatesService candidates;

    public ProfileController(OwnerResolver owners, ProfileService profiles, CandidatesService candidates) {
        this.owners = owners;
        this.profiles = profiles;
        this.candidates = candidates;
    }

    @GetMapping("/profile")
    public ProfileView get(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return profiles.get(owners.requireOwner(authorization));
    }

    @PutMapping("/profile")
    public ProfileView update(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody ProfileUpdateRequest request) {
        return profiles.update(owners.requireOwner(authorization), request);
    }

    @PutMapping("/password")
    public Map<String, Boolean> changePassword(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody PasswordChangeRequest request) {
        candidates.changePassword(
                owners.requireOwner(authorization),
                request.newPassword());
        return Map.of("changed", true);
    }
}
