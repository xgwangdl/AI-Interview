package com.guangge.Interview.profile;

import com.guangge.Interview.data.Candidates;
import com.guangge.Interview.repository.CandidatesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileServiceTest {

    private CandidatesRepository repository;
    private ProfileService service;

    @BeforeEach
    void setUp() {
        repository = mock(CandidatesRepository.class);
        service = new ProfileService(repository);
        when(repository.save(any(Candidates.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void returnsSafeDefaultsForAnExistingAccount() {
        Candidates candidate = candidate("alice");
        when(repository.findFirstByName("alice")).thenReturn(Optional.of(candidate));

        ProfileView profile = service.get("alice");

        assertEquals("alice", profile.loginName());
        assertEquals("alice", profile.displayName());
        assertEquals("Asia/Shanghai", profile.timezone());
        assertEquals("standard", profile.fontScale());
        assertEquals("green", profile.theme());
        assertFalse(profile.reduceMotion());
    }

    @Test
    void updatesProfileAndAppearanceWithoutChangingLoginName() {
        Candidates candidate = candidate("alice");
        when(repository.findFirstByName("alice")).thenReturn(Optional.of(candidate));
        ProfileUpdateRequest request = new ProfileUpdateRequest(
                "青禾", "  qinghe@example.com ", "Asia/Tokyo", "large", true, "blue");

        ProfileView profile = service.update("alice", request);

        assertEquals("alice", candidate.getName());
        assertEquals("青禾", candidate.getDisplayName());
        assertEquals("qinghe@example.com", candidate.getEmail());
        assertEquals("Asia/Tokyo", candidate.getTimezone());
        assertEquals("large", candidate.getFontScale());
        assertEquals(true, candidate.isReduceMotion());
        assertEquals("blue", candidate.getTheme());
        assertEquals("青禾", profile.displayName());
    }

    @Test
    void allowsClearingEmail() {
        Candidates candidate = candidate("alice");
        candidate.setEmail("old@example.com");
        when(repository.findFirstByName("alice")).thenReturn(Optional.of(candidate));

        service.update("alice",
                new ProfileUpdateRequest("青禾", "   ", "Asia/Shanghai", "standard", false, "green"));

        assertNull(candidate.getEmail());
    }

    @Test
    void rejectsInvalidNicknameTimezoneFontScaleAndTheme() {
        when(repository.findFirstByName("alice")).thenReturn(Optional.of(candidate("alice")));

        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.update("alice",
                new ProfileUpdateRequest("A", null, "Asia/Shanghai", "standard", false, "green")))
                .getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.update("alice",
                new ProfileUpdateRequest("青禾", null, "Mars/Olympus", "standard", false, "green")))
                .getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.update("alice",
                new ProfileUpdateRequest("青禾", null, "Asia/Shanghai", "huge", false, "green")))
                .getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.update("alice",
                new ProfileUpdateRequest("青禾", null, "Asia/Shanghai", "standard", false, "purple")))
                .getStatusCode().value());
    }

    private static Candidates candidate(String name) {
        Candidates candidate = new Candidates();
        candidate.setName(name);
        candidate.setCode("$2a$10$placeholder");
        candidate.setCreatedAt(Instant.parse("2026-01-02T03:04:05Z"));
        return candidate;
    }
}
