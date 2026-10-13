package com.guangge.Interview.practice.media;

import com.guangge.Interview.practice.dto.PracticeAnswerView;
import com.guangge.Interview.practice.security.OwnerResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;

/**
 * 分片/断点续传接口（#12）。
 *
 * 与单段上传（{@code POST /api/v1/answers/{id}/media}）并存，客户端可按需选择：
 * 小文件走单段，大文件走分片。整条链路：
 * <pre>
 *   GET    /upload?totalChunks=N        查询已收到的分片（刷新后据此续传）
 *   PUT    /chunks/{index}?totalChunks=N 上传单个分片（application/octet-stream）
 *   POST   /merge                       合并 + 校验 sha256 + 入队分析
 *   DELETE /upload?totalChunks=N        放弃上传并清理临时分片
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/answers/{answerId}/media")
public class ChunkUploadController {

    private final OwnerResolver owners;
    private final ChunkedUploadService uploads;

    public ChunkUploadController(OwnerResolver owners, ChunkedUploadService uploads) {
        this.owners = owners;
        this.uploads = uploads;
    }

    @GetMapping("/upload")
    public MediaUploadStatusView status(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID answerId,
            @RequestParam int totalChunks) {
        return uploads.status(owners.requireOwner(authorization), answerId, totalChunks);
    }

    @PutMapping(value = "/chunks/{index}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public MediaUploadStatusView uploadChunk(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID answerId,
            @PathVariable int index,
            @RequestParam int totalChunks,
            HttpServletRequest request,
            @RequestBody byte[] content) throws IOException {
        long declared = request.getContentLengthLong();
        if (declared > ChunkedUploadService.MAX_CHUNK_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "单个分片不得超过 " + ChunkedUploadService.MAX_CHUNK_BYTES + " 字节");
        }
        return uploads.storeChunk(owners.requireOwner(authorization), answerId, index, totalChunks, content);
    }

    @PostMapping("/merge")
    public PracticeAnswerView merge(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID answerId,
            @Validated @RequestBody MediaMergeRequest request) throws IOException {
        return uploads.merge(owners.requireOwner(authorization), answerId, request);
    }

    @DeleteMapping("/upload")
    public ResponseEntity<Void> abort(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID answerId,
            @RequestParam int totalChunks) throws IOException {
        uploads.abort(owners.requireOwner(authorization), answerId, totalChunks);
        return ResponseEntity.noContent().build();
    }
}
