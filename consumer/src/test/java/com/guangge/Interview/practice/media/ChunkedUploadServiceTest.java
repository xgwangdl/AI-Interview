package com.guangge.Interview.practice.media;

import com.guangge.Interview.analysis.AnalysisJobService;
import com.guangge.Interview.practice.dto.PracticeAnswerView;
import com.guangge.Interview.practice.service.PracticeSessionService;
import com.guangge.Interview.practice.storage.LocalMediaStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** #12：分片上传、断点续传与合并完整性校验。 */
class ChunkedUploadServiceTest {

    @TempDir
    Path tempDir;

    private LocalMediaStorage storage;
    private PracticeSessionService sessions;
    private AnalysisJobService analysisJobs;
    private ChunkedUploadService uploads;
    private UUID answerId;

    @BeforeEach
    void setUp() {
        storage = new LocalMediaStorage(tempDir.toString());
        sessions = mock(PracticeSessionService.class);
        analysisJobs = mock(AnalysisJobService.class);
        uploads = new ChunkedUploadService(storage, sessions, analysisJobs);
        answerId = UUID.randomUUID();
    }

    @Test
    void reportsReceivedChunksSoTheClientCanResume() throws Exception {
        uploads.storeChunk("alice", answerId, 0, 3, bytes("abc"));
        uploads.storeChunk("alice", answerId, 2, 3, bytes("ghi"));

        MediaUploadStatusView status = uploads.status("alice", answerId, 3);

        assertEquals(List.of(0, 2), status.receivedChunks());
        assertFalse(status.readyToMerge(), "缺第 1 片时不应该认为可以合并");
    }

    @Test
    void mergesChunksIntoTheStoredMediaAndEnqueuesAnalysis() throws Exception {
        byte[] part1 = bytes("hello-");
        byte[] part2 = bytes("world");
        uploads.storeChunk("alice", answerId, 0, 2, part1);
        uploads.storeChunk("alice", answerId, 1, 2, part2);
        assertTrue(uploads.status("alice", answerId, 2).readyToMerge());

        AtomicReference<byte[]> stored = new AtomicReference<>();
        when(sessions.storeAnswerMedia(eq("alice"), eq(answerId), eq("video/webm"), eq(11L), any()))
                .thenAnswer(invocation -> {
                    InputStream content = invocation.getArgument(4);
                    stored.set(content.readAllBytes());
                    return answerView(11L);
                });

        PracticeAnswerView view = uploads.merge("alice", answerId,
                new MediaMergeRequest(2, 11, "video/webm", sha256(concat(part1, part2))));

        assertEquals(answerId, view.id());
        assertArrayEquals(concat(part1, part2), stored.get(), "合并结果必须与上传的分片一致");
        verify(analysisJobs).enqueueAnswer(answerId);
        assertTrue(storage.find(chunkKey(0)).isEmpty(), "合并成功后应清理临时分片");
        assertTrue(storage.find(chunkKey(1)).isEmpty());
    }

    @Test
    void rejectsMergeWhenTheChecksumDoesNotMatch() throws Exception {
        byte[] part1 = bytes("hello-");
        byte[] part2 = bytes("world");
        uploads.storeChunk("alice", answerId, 0, 2, part1);
        uploads.storeChunk("alice", answerId, 1, 2, part2);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> uploads.merge("alice", answerId,
                        new MediaMergeRequest(2, 11, "video/webm", "deadbeef")));

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("sha256"), exception.getReason());
        verify(sessions, never()).storeAnswerMedia(any(), any(), any(), anyLong(), any());
        // 校验失败时保留分片，客户端可以补传/重试，而不是从头再来。
        assertTrue(storage.find(chunkKey(0)).isPresent());
    }

    @Test
    void rejectsMergeWhileChunksAreStillMissing() throws Exception {
        uploads.storeChunk("alice", answerId, 0, 3, bytes("abc"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> uploads.merge("alice", answerId,
                        new MediaMergeRequest(3, 9, "video/webm", null)));

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("缺少分片"), exception.getReason());
    }

    @Test
    void abortRemovesTemporaryChunks() throws Exception {
        uploads.storeChunk("alice", answerId, 0, 2, bytes("abc"));
        uploads.storeChunk("alice", answerId, 1, 2, bytes("def"));

        uploads.abort("alice", answerId, 2);

        assertTrue(uploads.status("alice", answerId, 2).receivedChunks().isEmpty());
    }

    @Test
    void rejectsChunkIndexOutsideTheDeclaredRange() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> uploads.storeChunk("alice", answerId, 5, 2, bytes("abc")));

        assertEquals(400, exception.getStatusCode().value());
    }

    private PracticeAnswerView answerView(long size) {
        return new PracticeAnswerView(answerId, 0, "q-0", 1, true, "video/webm", size, 30, 0,
                "awaiting_provider", null, null, null, "awaiting_provider", null, null);
    }

    private String chunkKey(int index) {
        return "uploads/" + answerId + "/chunk-" + index;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static String sha256(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(content));
    }
}
