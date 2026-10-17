package com.guangge.Interview.services;

import com.guangge.Interview.data.CandidateStatus;
import com.guangge.Interview.data.Candidates;
import com.guangge.Interview.repository.CandidatesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 注册与登录（口令哈希 + 历史明文兼容）。 */
class CandidatesServiceTest {

    private CandidatesRepository repository;
    private PasswordEncoder encoder;
    private CandidatesService service;

    @BeforeEach
    void setUp() {
        repository = mock(CandidatesRepository.class);
        encoder = new BCryptPasswordEncoder();
        service = new CandidatesService(repository, encoder);
        when(repository.save(any(Candidates.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void registersWithAHashedPassword() {
        when(repository.existsByName("alice")).thenReturn(false);

        Candidates created = service.register("alice", "secret123", "alice@example.com");

        assertEquals("alice", created.getName());
        assertNotEquals("secret123", created.getCode(), "口令不能明文落库");
        assertTrue(created.getCode().startsWith("$2"), "应当是 BCrypt 哈希");
        assertTrue(encoder.matches("secret123", created.getCode()));
        assertEquals(CandidateStatus.WAIT, created.getStatus());
        assertEquals("alice@example.com", created.getEmail());
    }

    @Test
    void acceptsChineseUserNames() {
        when(repository.existsByName("张伟")).thenReturn(false);

        Candidates created = service.register("张伟", "secret123", null);

        assertEquals("张伟", created.getName());
    }

    @Test
    void trimsUserNameAndRejectsDuplicates() {
        when(repository.existsByName("alice")).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.register("  alice  ", "secret123", null));

        assertEquals(409, exception.getStatusCode().value());
        verify(repository, never()).save(any(Candidates.class));
    }

    @Test
    void rejectsShortPasswordAndInvalidName() {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.register("alice", "12345", null)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.register("a", "secret123", null)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.register("alice smith", "secret123", null)).getStatusCode().value());
    }

    @Test
    void logsInWithAHashedPassword() {
        Candidates stored = candidate("alice", encoder.encode("secret123"));
        when(repository.findFirstByName("alice")).thenReturn(Optional.of(stored));

        Candidates loggedIn = service.login("alice", "secret123");

        assertEquals("alice", loggedIn.getName());
        verify(repository, never()).save(any(Candidates.class));
    }

    @Test
    void rejectsAWrongPassword() {
        Candidates stored = candidate("alice", encoder.encode("secret123"));
        when(repository.findFirstByName("alice")).thenReturn(Optional.of(stored));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.login("alice", "wrong-password"));

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void rejectsAnUnknownUser() {
        when(repository.findFirstByName("nobody")).thenReturn(Optional.empty());

        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.login("nobody", "secret123")).getStatusCode().value());
    }

    /**
     * 历史数据（手工 INSERT 的 smoke/123456）存的是明文邀请码。
     * 这类账号必须还能登录，并且登录成功后自动升级成哈希，否则永远停在明文。
     */
    @Test
    void upgradesLegacyPlaintextCodesOnSuccessfulLogin() {
        Candidates legacy = candidate("smoke", "123456");
        when(repository.findFirstByName("smoke")).thenReturn(Optional.of(legacy));

        Candidates loggedIn = service.login("smoke", "123456");

        assertTrue(loggedIn.getCode().startsWith("$2"), "登录成功后应改写为 BCrypt 哈希");
        assertTrue(encoder.matches("123456", loggedIn.getCode()));
        verify(repository).save(legacy);
    }

    @Test
    void rejectsAWrongLegacyPlaintextCode() {
        Candidates legacy = candidate("smoke", "123456");
        when(repository.findFirstByName("smoke")).thenReturn(Optional.of(legacy));

        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.login("smoke", "654321")).getStatusCode().value());
        verify(repository, never()).save(any(Candidates.class));
    }

    @Test
    void changesPasswordAfterVerifyingTheCurrentOne() {
        Candidates stored = candidate("alice", encoder.encode("secret123"));
        when(repository.findFirstByName("alice")).thenReturn(Optional.of(stored));

        service.changePassword("alice", "secret123", "new-secret");

        assertTrue(encoder.matches("new-secret", stored.getCode()));
        verify(repository).save(stored);
    }

    @Test
    void rejectsPasswordChangeWithWrongCurrentPassword() {
        Candidates stored = candidate("alice", encoder.encode("secret123"));
        when(repository.findFirstByName("alice")).thenReturn(Optional.of(stored));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.changePassword("alice", "wrong-password", "new-secret"));

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(encoder.matches("secret123", stored.getCode()));
        verify(repository, never()).save(any(Candidates.class));
    }

    @Test
    void rejectsTooShortNewPassword() {
        Candidates stored = candidate("alice", encoder.encode("secret123"));
        when(repository.findFirstByName("alice")).thenReturn(Optional.of(stored));

        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.changePassword("alice", "secret123", "12345")).getStatusCode().value());
        verify(repository, never()).save(any(Candidates.class));
    }

    private static Candidates candidate(String name, String code) {
        Candidates candidate = new Candidates();
        candidate.setName(name);
        candidate.setCode(code);
        return candidate;
    }
}
