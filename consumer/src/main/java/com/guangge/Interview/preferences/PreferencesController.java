package com.guangge.Interview.preferences;

import com.guangge.Interview.data.Candidates;
import com.guangge.Interview.practice.security.OwnerResolver;
import com.guangge.Interview.repository.CandidatesRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

/**
 * 个人偏好：让每个用户选自己的页面主题色。
 *
 * 主题本身只是一组 CSS 变量，前端换色不需要后端参与；这里持久化的是「这个用户选了哪个」，
 * 这样换台机器登录也能保持。
 */
@RestController
@RequestMapping("/api/v1/me")
public class PreferencesController {

    /** 与前端 services/theme.js 里的 THEMES 保持一致。 */
    private static final Set<String> ALLOWED_THEMES = Set.of("green", "blue", "amber");
    private static final String DEFAULT_THEME = "green";

    private final OwnerResolver owners;
    private final CandidatesRepository candidates;

    public PreferencesController(OwnerResolver owners, CandidatesRepository candidates) {
        this.owners = owners;
        this.candidates = candidates;
    }

    @GetMapping("/preferences")
    public Map<String, String> preferences(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        Candidates candidate = requireCandidate(authorization);
        String theme = candidate.getTheme();
        return Map.of("theme", theme == null || theme.isBlank() ? DEFAULT_THEME : theme);
    }

    @PutMapping("/preferences")
    public Map<String, String> update(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody PreferencesRequest request) {
        String theme = request.theme().trim().toLowerCase();
        if (!ALLOWED_THEMES.contains(theme)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知主题色：" + theme);
        }
        Candidates candidate = requireCandidate(authorization);
        candidate.setTheme(theme);
        candidates.save(candidate);
        return Map.of("theme", theme);
    }

    private Candidates requireCandidate(String authorization) {
        String owner = owners.requireOwner(authorization);
        return candidates.findFirstByName(owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "账号不存在"));
    }
}
