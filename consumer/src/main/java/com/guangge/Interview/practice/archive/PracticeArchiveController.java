package com.guangge.Interview.practice.archive;

import com.guangge.Interview.practice.dto.PracticeArchiveView;
import com.guangge.Interview.practice.dto.PracticeSessionView;
import com.guangge.Interview.practice.mistakes.MistakeBookService;
import com.guangge.Interview.practice.mistakes.MistakeBookView;
import com.guangge.Interview.practice.security.OwnerResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 训练档案接口：跨场次的成长与短板。 */
@RestController
@RequestMapping("/api/v1/practice-archive")
public class PracticeArchiveController {

    private final OwnerResolver owners;
    private final PracticeArchiveService archive;
    private final PracticeScoreArchiveService scores;
    private final MistakeBookService mistakeBook;

    public PracticeArchiveController(OwnerResolver owners, PracticeArchiveService archive,
                                     PracticeScoreArchiveService scores, MistakeBookService mistakeBook) {
        this.owners = owners;
        this.archive = archive;
        this.scores = scores;
        this.mistakeBook = mistakeBook;
    }

    @GetMapping
    public PracticeArchiveView summary(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(defaultValue = "5") int limit) {
        return archive.summary(owners.requireOwner(authorization), limit);
    }

    /**
     * 清空训练档案。
     *
     * 只清长期分数统计，练习与录像仍按各自的保留策略走 —— 用户想"把成长记录抹掉"时用它。
     */
    @DeleteMapping
    public Map<String, Integer> clear(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return Map.of("removed", scores.clear(owners.requireOwner(authorization)));
    }

    /** 错题本：最好一次也没过线的题。 */
    @GetMapping("/mistakes")
    public MistakeBookView mistakes(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(defaultValue = "5") int limit) {
        return mistakeBook.summary(owners.requireOwner(authorization), limit);
    }

    /**
     * 重练这道题。
     *
     * 返回的就是一场正常的练习（只有这一道题），前端拿到 id 后照常走设备检测 → 录制 → 报告，
     * 不需要为新流程再写一套界面。
     */
    @PostMapping("/mistakes/{questionId}/retry")
    public ResponseEntity<PracticeSessionView> retryMistake(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String questionId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mistakeBook.retry(owners.requireOwner(authorization), questionId));
    }
}
