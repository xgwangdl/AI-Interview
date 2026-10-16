package com.guangge.Interview.resume.export;

import com.guangge.Interview.repository.CandidatesRepository;
import org.springframework.stereotype.Component;

/** 从账号偏好里取导出用的强调色，取不到就用默认配色。 */
@Component
public class ResumeThemeResolver {

    private final CandidatesRepository candidates;

    public ResumeThemeResolver(CandidatesRepository candidates) {
        this.candidates = candidates;
    }

    public ResumeTheme themeOf(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) return ResumeTheme.GREEN;
        return candidates.findFirstByName(ownerId)
                .map(candidate -> ResumeTheme.parse(candidate.getTheme()))
                .orElse(ResumeTheme.GREEN);
    }
}
