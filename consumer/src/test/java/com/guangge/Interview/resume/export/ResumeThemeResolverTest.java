package com.guangge.Interview.resume.export;

import com.guangge.Interview.data.Candidates;
import com.guangge.Interview.repository.CandidatesRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 导出配色取自账号偏好，取不到就用默认，不能因为查不到账号而让导出失败。 */
class ResumeThemeResolverTest {

    private final CandidatesRepository candidates = mock(CandidatesRepository.class);
    private final ResumeThemeResolver resolver = new ResumeThemeResolver(candidates);

    @Test
    @DisplayName("账号里存了主题就按主题导出")
    void readsThemeFromAccount() {
        Candidates candidate = new Candidates();
        candidate.setTheme("amber");
        when(candidates.findFirstByName("guangge")).thenReturn(Optional.of(candidate));

        assertThat(resolver.themeOf("guangge")).isEqualTo(ResumeTheme.AMBER);
    }

    @Test
    @DisplayName("没设置过主题、账号不存在、未知取值都退回默认绿")
    void fallsBackToDefault() {
        Candidates blank = new Candidates();
        when(candidates.findFirstByName("blank")).thenReturn(Optional.of(blank));
        Candidates unknown = new Candidates();
        unknown.setTheme("紫罗兰");
        when(candidates.findFirstByName("unknown")).thenReturn(Optional.of(unknown));
        when(candidates.findFirstByName("ghost")).thenReturn(Optional.empty());

        assertThat(resolver.themeOf("blank")).isEqualTo(ResumeTheme.GREEN);
        assertThat(resolver.themeOf("unknown")).isEqualTo(ResumeTheme.GREEN);
        assertThat(resolver.themeOf("ghost")).isEqualTo(ResumeTheme.GREEN);
        assertThat(resolver.themeOf(null)).isEqualTo(ResumeTheme.GREEN);
    }
}
