package com.guangge.Interview.jobradar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.data.Candidates;
import com.guangge.Interview.repository.CandidatesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SkillProfileServiceTest {
    private CandidatesRepository candidates;
    private SkillProfileService service;

    @BeforeEach
    void setUp() {
        candidates = mock(CandidatesRepository.class);
        service = new SkillProfileService(candidates, new ObjectMapper());
    }

    @Test
    void extractionIsPreviewOnlyAndNeverWritesTheCandidateProfile() {
        SkillProfileView preview = service.extract(
                new SkillProfileExtractRequest("5 年开发经验，掌握 Java、Spring Boot、PostgreSQL 和 Redis，现居上海。"));

        assertThat(preview.configured()).isFalse();
        assertThat(preview.skills()).extracting(SkillProfileView.SkillItem::name)
                .contains("Java", "Spring Boot", "PostgreSQL", "Redis");
        assertThat(preview.years()).isEqualTo(5);
        assertThat(preview.city()).isEqualTo("上海");
        verifyNoInteractions(candidates);
    }

    @Test
    void confirmedUpdatePersistsTheEditableProfile() {
        Candidates candidate = new Candidates();
        candidate.setName("alice");
        when(candidates.findFirstByName("alice")).thenReturn(Optional.of(candidate));
        when(candidates.save(candidate)).thenReturn(candidate);
        SkillProfileUpdateRequest request = new SkillProfileUpdateRequest(
                List.of(new SkillProfileUpdateRequest.SkillItem("Java", 4, new BigDecimal("1.0")),
                        new SkillProfileUpdateRequest.SkillItem("Spring Boot", 5, new BigDecimal("1.2"))),
                6, "杭州", 30, 45);

        SkillProfileView saved = service.update("alice", request);

        assertThat(saved.configured()).isTrue();
        assertThat(saved.city()).isEqualTo("杭州");
        assertThat(saved.skills()).hasSize(2);
        assertThat(candidate.getSkillProfileJson()).isNotNull();
        verify(candidates).save(candidate);
    }

    @Test
    void duplicateCanonicalSkillsAreRejectedBeforeWriting() {
        SkillProfileUpdateRequest request = new SkillProfileUpdateRequest(
                List.of(new SkillProfileUpdateRequest.SkillItem("Spring Boot", 3, BigDecimal.ONE),
                        new SkillProfileUpdateRequest.SkillItem("springboot", 4, BigDecimal.ONE)),
                null, null, null, null);

        assertThatThrownBy(() -> service.update("alice", request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(candidates);
    }
}
