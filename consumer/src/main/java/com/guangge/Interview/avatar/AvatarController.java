package com.guangge.Interview.avatar;

import com.guangge.Interview.practice.dto.PracticeQuestion;
import com.guangge.Interview.practice.dto.PracticeReportView;
import com.guangge.Interview.practice.dto.PracticeSessionView;
import com.guangge.Interview.practice.security.OwnerResolver;
import com.guangge.Interview.practice.service.PracticeReportService;
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
    private final PracticeReportService practiceReports;
    private final AvatarProvider avatarProvider;

    public AvatarController(
            OwnerResolver owners,
            PracticeSessionService practiceSessions,
            PracticeReportService practiceReports,
            AvatarProvider avatarProvider) {
        this.owners = owners;
        this.practiceSessions = practiceSessions;
        this.practiceReports = practiceReports;
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
     * 示范回答的播报（报告页"让面试官念一遍"）。
     *
     * 文本直接取评分时已经存下的 {@code sampleAnswer}，**不额外调用模型**，只做一次语音合成；
     * 复用的就是上面那套 Provider 与降级逻辑（合成不可用同样是 503）。
     */
    @GetMapping("/{id}/questions/{questionIndex}/sample-answer")
    public AvatarNarration sampleAnswer(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @PathVariable int questionIndex) {
        return avatarProvider.narrate(sampleAnswerRequest(authorization, id, questionIndex));
    }

    @GetMapping(value = "/{id}/questions/{questionIndex}/sample-answer/audio")
    public ResponseEntity<byte[]> sampleAnswerAudio(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @PathVariable int questionIndex) {
        SpeechAudio audio = synthesize(sampleAnswerRequest(authorization, id, questionIndex));
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
                session.id(), questionIndex, question.text(), session.interviewerStyle(), session.role(),
                "/api/v1/practice-sessions/" + session.id() + "/questions/" + questionIndex + "/narration/audio");
    }

    private AvatarNarrationRequest sampleAnswerRequest(String authorization, UUID id, int questionIndex) {
        String owner = owners.requireOwner(authorization);
        // 走报告：它已经做了归属校验，也把评分（含 sampleAnswer）组装好了，不必再查一遍库
        PracticeReportView report = practiceReports.report(owner, id);
        // 注意：report.questions() **只包含有作答的题**，所以数组下标不等于题号，必须按 questionIndex 找。
        PracticeReportView.QuestionReport item = report.questions().stream()
                .filter(question -> question.questionIndex() == questionIndex)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "This question has no answer yet"));
        String sample = item.evaluation() == null ? null : item.evaluation().sampleAnswer();
        if (sample == null || sample.isBlank()) {
            // 还没评分成功时不要念空字符串，直接告诉前端"这道题暂时没有示范回答"
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "This question has no sample answer yet (analysis may still be running)");
        }
        return new AvatarNarrationRequest(
                // interviewerStyle 只用于面试间的字幕标签，报告页不展示，这里给默认值即可
                id, questionIndex, sample, "balanced", report.role(),
                "/api/v1/practice-sessions/" + id + "/questions/" + questionIndex + "/sample-answer/audio");
    }
}
