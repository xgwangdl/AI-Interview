package com.guangge.Interview.practice.media;

import com.guangge.Interview.analysis.AnalysisJobService;
import com.guangge.Interview.practice.dto.PracticeAnswerView;
import com.guangge.Interview.practice.service.PracticeSessionService;
import com.guangge.Interview.practice.storage.MediaStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 分片上传与断点续传（#12）。
 *
 * 上传标识直接复用 answerId：一场练习里每道题只有一段录像，天然一一对应，
 * 因此不需要额外的「上传会话」表，客户端只要问服务端「哪些分片已经在」就能续传。
 *
 * 分片落到 {@code uploads/{answerId}/chunk-N}，合并时按序拼成一个流写入正式的媒体 key，
 * 合并成功后删除临时分片，并把该题加入分析队列。
 */
@Service
public class ChunkedUploadService {

    private static final Logger log = LoggerFactory.getLogger(ChunkedUploadService.class);

    /** 单个分片的上限，防止用超大 body 撑爆内存。 */
    public static final long MAX_CHUNK_BYTES = 32L * 1024 * 1024;
    private static final int MAX_CHUNKS = 1000;
    private static final String CHUNK_ROOT = "uploads";

    private final MediaStorage mediaStorage;
    private final PracticeSessionService sessions;
    private final AnalysisJobService analysisJobs;

    public ChunkedUploadService(MediaStorage mediaStorage,
                                PracticeSessionService sessions,
                                AnalysisJobService analysisJobs) {
        this.mediaStorage = mediaStorage;
        this.sessions = sessions;
        this.analysisJobs = analysisJobs;
    }

    /** 已收到的分片列表；客户端据此只补传缺失部分。 */
    public MediaUploadStatusView status(String ownerId, UUID answerId, int totalChunks) {
        sessions.assertOwnedAnswer(ownerId, answerId);
        return statusOf(answerId, requireTotalChunks(totalChunks));
    }

    public MediaUploadStatusView storeChunk(String ownerId, UUID answerId, int index, int totalChunks, byte[] content)
            throws IOException {
        sessions.assertOwnedAnswer(ownerId, answerId);
        int total = requireTotalChunks(totalChunks);
        if (index < 0 || index >= total) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分片序号越界：" + index);
        }
        if (content == null || content.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分片内容为空");
        }
        if (content.length > MAX_CHUNK_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "单个分片不得超过 " + MAX_CHUNK_BYTES + " 字节");
        }
        mediaStorage.store(chunkKey(answerId, index), new ByteArrayInputStream(content), content.length);
        return statusOf(answerId, total);
    }

    /**
     * 合并分片：先按序算一遍 SHA-256 校验完整性，再拼成完整媒体写入正式 key。
     *
     * 分两遍读取是为了「校验失败不落库」：先验证，再写入，避免用坏数据覆盖已有录像。
     */
    public PracticeAnswerView merge(String ownerId, UUID answerId, MediaMergeRequest request) throws IOException {
        sessions.assertOwnedAnswer(ownerId, answerId);
        int total = requireTotalChunks(request.totalChunks());
        String contentType = request.contentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("video/")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "A video file is required");
        }

        List<Integer> missing = missingChunks(answerId, total);
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少分片 " + missing + "，请补传后再合并");
        }

        if (request.checksumSha256() != null && !request.checksumSha256().isBlank()) {
            String actual = sha256(answerId, total);
            if (!actual.equalsIgnoreCase(request.checksumSha256().trim())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "分片完整性校验失败：sha256 不匹配（期望 " + request.checksumSha256()
                                + "，实际 " + actual + "）");
            }
        }

        PracticeAnswerView answer;
        try (InputStream merged = openChunks(answerId, total)) {
            answer = sessions.storeAnswerMedia(ownerId, answerId, contentType, request.totalBytes(), merged);
        }

        deleteChunks(answerId, total);
        // 合并完成即触发分析；enqueueAnswer 幂等，客户端随后再调 /complete 也不会重复入队。
        analysisJobs.enqueueAnswer(answerId);
        log.info("分片合并完成 answer={} chunks={} bytes={}", answerId, total, request.totalBytes());
        return answer;
    }

    /** 放弃本次分片上传并清掉临时分片（客户端重新录制时调用）。 */
    public void abort(String ownerId, UUID answerId, int totalChunks) throws IOException {
        sessions.assertOwnedAnswer(ownerId, answerId);
        deleteChunks(answerId, requireTotalChunks(totalChunks));
    }

    private MediaUploadStatusView statusOf(UUID answerId, int total) {
        List<Integer> received = new ArrayList<>();
        for (int index = 0; index < total; index++) {
            if (mediaStorage.find(chunkKey(answerId, index)).isPresent()) received.add(index);
        }
        return new MediaUploadStatusView(answerId, total, received, received.size() == total);
    }

    private List<Integer> missingChunks(UUID answerId, int total) {
        List<Integer> missing = new ArrayList<>();
        for (int index = 0; index < total; index++) {
            if (mediaStorage.find(chunkKey(answerId, index)).isEmpty()) missing.add(index);
        }
        return missing;
    }

    private String sha256(UUID answerId, int total) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
        byte[] buffer = new byte[64 * 1024];
        try (InputStream merged = openChunks(answerId, total)) {
            int read;
            while ((read = merged.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** 按序号打开所有分片并拼成一个连续流；调用方负责关闭。 */
    private InputStream openChunks(UUID answerId, int total) throws IOException {
        List<InputStream> streams = new ArrayList<>(total);
        try {
            for (int index = 0; index < total; index++) {
                int chunkIndex = index;
                Resource resource = mediaStorage.find(chunkKey(answerId, chunkIndex))
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "缺少分片 " + chunkIndex + "，请补传后再合并"));
                streams.add(resource.getInputStream());
            }
        } catch (RuntimeException | IOException exception) {
            closeAll(streams);
            throw exception;
        }
        return new SequenceInputStream(Collections.enumeration(streams));
    }

    private void deleteChunks(UUID answerId, int total) throws IOException {
        for (int index = 0; index < total; index++) {
            try {
                mediaStorage.delete(chunkKey(answerId, index));
            } catch (IOException exception) {
                // 临时分片清理失败不该让已经成功的合并失败，留日志即可。
                log.warn("清理分片失败 answer={} index={}: {}", answerId, index, exception.getMessage());
            }
        }
    }

    private static void closeAll(List<InputStream> streams) {
        for (InputStream stream : streams) {
            try {
                stream.close();
            } catch (IOException ignored) {
                // 关闭失败无需处理，原始异常更重要。
            }
        }
    }

    private static int requireTotalChunks(int totalChunks) {
        if (totalChunks < 1 || totalChunks > MAX_CHUNKS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "totalChunks 必须在 1 到 " + MAX_CHUNKS + " 之间");
        }
        return totalChunks;
    }

    private static String chunkKey(UUID answerId, int index) {
        return CHUNK_ROOT + "/" + answerId + "/chunk-" + index;
    }
}
