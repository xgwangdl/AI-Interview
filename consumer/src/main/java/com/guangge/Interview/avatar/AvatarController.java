package com.guangge.Interview.avatar;

import com.guangge.Interview.practice.dto.PracticeQuestion;
import com.guangge.Interview.practice.dto.PracticeSessionView;
import com.guangge.Interview.practice.security.OwnerResolver;
import com.guangge.Interview.practice.service.PracticeSessionService;
import com.guangge.Interview.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/practice-sessions")
public class AvatarController {

    private static final Logger log = LoggerFactory.getLogger(AvatarController.class);

    private final OwnerResolver owners;
    private final PracticeSessionService practiceSessions;
    private final AvatarProvider avatarProvider;

    public AvatarController(
            OwnerResolver owners,
            PracticeSessionService practiceSessions,
            AvatarProvider avatarProvider) {
        this.owners = owners;
        this.practiceSessions = practiceSessions;
        this.avatarProvider = avatarProvider;
    }

    @GetMapping("/{id}/questions/{questionIndex}/narration")
    public AvatarNarration narration(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @PathVariable int questionIndex) {
        return avatarProvider.narrate(request(authorization, id, questionIndex));
    }

    @GetMapping(value = "/{id}/questions/{questionIndex}/narration/audio")
    public ResponseEntity<byte[]> narrationAudio(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @PathVariable int questionIndex) {
        SpeechAudio audio = synthesize(request(authorization, id, questionIndex));
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .contentType(MediaType.parseMediaType(audio.contentType()))
                .body(audio.audio());
    }

    /**
     * 合成失败要变成明确的 503，客户端据此把数字人降级成「头像 + 文字」。
     * 放任供应商异常冒泡只会得到 500 和一段默认错误页。
     */
    private SpeechAudio synthesize(AvatarNarrationRequest request) {
        try {
            return avatarProvider.speech(request)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE, "语音合成暂不可用"));
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("面试官语音合成失败: {}", LogSanitizer.sanitize(exception.getMessage()));
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "语音合成暂不可用");
        }
    }

    private AvatarNarrationRequest request(String authorization, UUID id, int questionIndex) {
        PracticeSessionView session = practiceSessions.get(owners.requireOwner(authorization), id);
        if (questionIndex < 0 || questionIndex >= session.questions().size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found in this session");
        }
        PracticeQuestion question = session.questions().get(questionIndex);
        return new AvatarNarrationRequest(
                session.id(), questionIndex, question.text(), session.interviewerStyle(), session.role());
    }
}
