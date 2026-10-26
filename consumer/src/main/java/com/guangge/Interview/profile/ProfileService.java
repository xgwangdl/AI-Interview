package com.guangge.Interview.profile;

import com.guangge.Interview.data.Candidates;
import com.guangge.Interview.repository.CandidatesRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Set;

@Service
public class ProfileService {

    private static final Set<String> ALLOWED_THEMES = Set.of("green", "blue", "amber");
    private static final Set<String> ALLOWED_FONT_SCALES = Set.of("standard", "large", "xlarge");
    private static final Set<String> ALLOWED_LANGUAGES = Set.of("zh-CN", "en-US", "ja-JP");
    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";
    private static final String DEFAULT_FONT_SCALE = "standard";
    private static final String DEFAULT_THEME = "green";
    private static final String DEFAULT_LANGUAGE = "zh-CN";

    private final CandidatesRepository candidates;

    public ProfileService(CandidatesRepository candidates) {
        this.candidates = candidates;
    }

    @Transactional(readOnly = true)
    public ProfileView get(String owner) {
        return toView(requireCandidate(owner));
    }

    @Transactional
    public ProfileView update(String owner, ProfileUpdateRequest request) {
        Candidates candidate = requireCandidate(owner);

        String displayName = request.displayName().trim();
        if (displayName.length() < 2 || displayName.length() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "昵称长度需在 2 到 20 个字符之间");
        }

        String timezone = request.timezone().trim();
        validateTimezone(timezone);

        String fontScale = request.fontScale().trim().toLowerCase();
        if (!ALLOWED_FONT_SCALES.contains(fontScale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知界面字号：" + fontScale);
        }

        String theme = request.theme().trim().toLowerCase();
        if (!ALLOWED_THEMES.contains(theme)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知主题色：" + theme);
        }

        String language = request.language();
        if (language == null || language.isBlank()) {
            language = defaultIfBlank(candidate.getLanguage(), DEFAULT_LANGUAGE);
        } else {
            language = language.trim();
            if (!ALLOWED_LANGUAGES.contains(language)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知界面语言：" + language);
            }
        }

        candidate.setDisplayName(displayName);
        candidate.setEmail(normalizeOptional(request.email()));
        candidate.setTimezone(timezone);
        candidate.setFontScale(fontScale);
        candidate.setReduceMotion(request.reduceMotion());
        candidate.setTheme(theme);
        candidate.setLanguage(language);
        return toView(candidates.save(candidate));
    }

    private Candidates requireCandidate(String owner) {
        return candidates.findFirstByName(owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "账号不存在"));
    }

    private static void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知时区：" + timezone, exception);
        }
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static ProfileView toView(Candidates candidate) {
        return new ProfileView(
                candidate.getName(),
                defaultIfBlank(candidate.getDisplayName(), candidate.getName()),
                candidate.getEmail(),
                defaultIfBlank(candidate.getTimezone(), DEFAULT_TIMEZONE),
                defaultIfBlank(candidate.getFontScale(), DEFAULT_FONT_SCALE),
                candidate.isReduceMotion(),
                defaultIfBlank(candidate.getTheme(), DEFAULT_THEME),
                defaultIfBlank(candidate.getLanguage(), DEFAULT_LANGUAGE),
                candidate.getCreatedAt());
    }
}
