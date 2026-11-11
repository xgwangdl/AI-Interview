package com.guangge.Interview.jobradar;

import com.guangge.Interview.practice.security.OwnerResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/skill-profile")
public class SkillProfileController {
    private final OwnerResolver owners;
    private final SkillProfileService profiles;

    public SkillProfileController(OwnerResolver owners, SkillProfileService profiles) {
        this.owners = owners;
        this.profiles = profiles;
    }

    @GetMapping
    public SkillProfileView get(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return profiles.get(owners.requireOwner(authorization));
    }

    @PutMapping
    public SkillProfileView update(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody SkillProfileUpdateRequest request) {
        return profiles.update(owners.requireOwner(authorization), request);
    }

    @PostMapping("/extract")
    public SkillProfileView extract(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody SkillProfileExtractRequest request) {
        owners.requireOwner(authorization);
        return profiles.extract(request);
    }
}
