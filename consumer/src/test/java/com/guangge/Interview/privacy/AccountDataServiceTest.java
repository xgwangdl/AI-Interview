package com.guangge.Interview.privacy;

import com.guangge.Interview.practice.storage.MediaStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountDataServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MediaStorage media = mock(MediaStorage.class);
    private AccountDataService service;

    @BeforeEach
    void setUp() {
        service = new AccountDataService(jdbc, media);
        when(jdbc.queryForObject(contains("from candidates"), eq(Integer.class), eq("alice"))).thenReturn(1);
    }

    @Test
    void exportsOnlyTheCurrentAccountAndNeverIncludesThePasswordHash() {
        when(jdbc.queryForMap(contains("from candidates"), eq("alice"))).thenReturn(Map.of(
                "login_name", "alice",
                "email", "alice@example.com"));
        when(jdbc.queryForList(anyString(), eq("alice"))).thenReturn(List.of());

        Map<String, Object> exported = service.export("alice");

        assertEquals("1", String.valueOf(exported.get("schemaVersion")));
        assertEquals("alice", ((Map<?, ?>) exported.get("account")).get("login_name"));
        assertTrue(!String.valueOf(exported).contains("invitation_code"));
        verify(jdbc).queryForMap(contains("candidate_name"), eq("alice"));
    }

    @Test
    void deletesMediaBeforeRemovingTheAccountRow() throws Exception {
        when(jdbc.queryForList(contains("from practice_answers"), eq("alice")))
                .thenReturn(List.of(Map.of("media_key", "answer-media")));
        when(jdbc.queryForList(contains("left join language_turns"), eq("alice")))
                .thenReturn(List.of(Map.of(
                        "audio_key", "turn-audio",
                        "reply_audio_key", "reply-audio",
                        "opening_key", "opening-audio")));
        when(jdbc.update(anyString(), eq("alice"))).thenReturn(1);

        service.deleteAccount("alice", "alice");

        verify(media).delete("answer-media");
        verify(media).delete("turn-audio");
        verify(media).delete("reply-audio");
        verify(media).delete("opening-audio");
        verify(jdbc).update(contains("delete from candidates"), eq("alice"));
    }

    @Test
    void refusesToDeleteDatabaseRowsWhenMediaRemovalFails() throws Exception {
        when(jdbc.queryForList(contains("from practice_answers"), eq("alice")))
                .thenReturn(List.of(Map.of("media_key", "answer-media")));
        when(jdbc.queryForList(contains("left join language_turns"), eq("alice"))).thenReturn(List.of());
        doThrow(new IOException("storage unavailable")).when(media).delete("answer-media");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.deleteAccount("alice", "alice"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        verify(jdbc, never()).update(contains("delete from candidates"), eq("alice"));
    }

    @Test
    void requiresTypingTheExactLoginNameBeforeDeletion() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.deleteAccount("alice", "ALICE"));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }
}
