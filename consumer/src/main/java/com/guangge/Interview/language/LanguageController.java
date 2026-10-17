package com.guangge.Interview.language;

import com.guangge.Interview.practice.security.OwnerResolver;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/** 外语口语练习接口（英语 / 日语，L1 轮次对话）。 */
@Validated
@RestController
@RequestMapping("/api/v1")
public class LanguageController {

    private final OwnerResolver owners;
    private final LanguageCatalog catalog;
    private final LanguageSessionService sessions;

    public LanguageController(OwnerResolver owners, LanguageCatalog catalog, LanguageSessionService sessions) {
        this.owners = owners;
        this.catalog = catalog;
        this.sessions = sessions;
    }

    /** 语言 / 场景 / 等级 / 音色能力：前端据此决定用服务端音色还是浏览器语音。 */
    @GetMapping("/language-options")
    public LanguageOptionsView options(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        owners.requireOwner(authorization);
        return LanguageOptionsView.from(catalog);
    }

    @PostMapping("/language-sessions")
    public ResponseEntity<LanguageSessionView> create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody LanguageSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sessions.create(owners.requireOwner(authorization), request));
    }

    @GetMapping("/language-sessions")
    public List<LanguageSessionView> list(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return sessions.list(owners.requireOwner(authorization));
    }

    @GetMapping("/language-sessions/{id}")
    public LanguageSessionView get(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return sessions.get(owners.requireOwner(authorization), id);
    }

    /** 一轮对话：上传这一轮的录音（webm/ogg/wav），同步拿回听写、回复与建议。 */
    @PostMapping(value = "/language-sessions/{id}/turns", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LanguageTurnView turn(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @RequestPart("audio") MultipartFile audio,
            @RequestParam(value = "durationMs", required = false) Integer durationMs) throws IOException {
        String owner = owners.requireOwner(authorization);
        if (audio.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "这一轮没有录音");
        // 有些客户端（脚本、部分手机浏览器）会把音频标成 application/octet-stream：
        // 真正的格式由后端 ffmpeg 校验，这里只挡住明确的非音频类型。
        String contentType = audio.getContentType();
        if (contentType != null && !contentType.startsWith("audio/")
                && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "需要音频文件");
        }
        File temporary = Files.createTempFile("language-turn-", ".audio").toFile();
        try (InputStream content = audio.getInputStream()) {
            Files.copy(content, temporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return sessions.turn(owner, id, temporary, contentType, durationMs);
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
    }

    /** 手打一轮（识别不可用时的降级入口）。 */
    @PostMapping("/language-sessions/{id}/turns/text")
    public LanguageTurnView textTurn(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @Valid @RequestBody LanguageTurnTextRequest request) throws IOException {
        return sessions.turnText(owners.requireOwner(authorization), id, request.text());
    }

    @PostMapping("/language-sessions/{id}/finish")
    public LanguageSessionView finish(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return sessions.finish(owners.requireOwner(authorization), id);
    }

    @GetMapping("/language-sessions/{id}/turns/{turnIndex}/audio")
    public ResponseEntity<Resource> audio(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @PathVariable int turnIndex,
            @RequestParam(defaultValue = "user") String side) {
        LanguageSessionService.MediaFile media =
                sessions.media(owners.requireOwner(authorization), id, turnIndex, side);
        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (media.contentType() != null) {
            try {
                type = MediaType.parseMediaType(media.contentType());
            } catch (IllegalArgumentException ignored) {
                type = MediaType.APPLICATION_OCTET_STREAM;
            }
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .contentType(type)
                .body(media.resource());
    }

    @DeleteMapping("/language-sessions/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) throws IOException {
        sessions.delete(owners.requireOwner(authorization), id);
        return ResponseEntity.noContent().build();
    }
}
