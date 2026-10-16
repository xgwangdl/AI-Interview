package com.guangge.Interview.practice.archive;

import com.guangge.Interview.practice.dto.PracticeArchiveView;
import com.guangge.Interview.practice.security.OwnerResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    public PracticeArchiveController(OwnerResolver owners, PracticeArchiveService archive,
                                     PracticeScoreArchiveService scores) {
        this.owners = owners;
        this.archive = archive;
        this.scores = scores;
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
}
