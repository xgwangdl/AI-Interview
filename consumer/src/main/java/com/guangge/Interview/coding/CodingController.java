package com.guangge.Interview.coding;

import com.guangge.Interview.coding.dto.CodingAnswerRequest;
import com.guangge.Interview.coding.dto.CodingPlanRequest;
import com.guangge.Interview.coding.dto.CodingReportView;
import com.guangge.Interview.coding.dto.CodingSessionView;
import com.guangge.Interview.coding.dto.CodingSubmissionView;
import com.guangge.Interview.practice.security.OwnerResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 笔试模块接口（独立于视频面试）。
 *
 * 鉴权与视频练习一致：走 OwnerResolver，数据按 owner 隔离。
 */
@RestController
@RequestMapping("/api/v1/coding-sessions")
public class CodingController {

    private final OwnerResolver owners;
    private final CodingSessionService coding;
    private final CodeRunnerService runner;

    public CodingController(OwnerResolver owners, CodingSessionService coding, CodeRunnerService runner) {
        this.owners = owners;
        this.coding = coding;
        this.runner = runner;
    }

    /** 前端据此决定是否显示「运行」按钮以及可选语言。 */
    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        return Map.of(
                "codeRunnerEnabled", runner.enabled(),
                "maxConcurrency", runner.maxConcurrency(),
                "languages", Arrays.stream(CodeLanguage.values())
                        .map(language -> Map.of("id", language.id(), "needsCompile", language.needsCompile()))
                        .toList(),
                "types", Arrays.stream(QuestionType.values()).map(QuestionType::id).toList());
    }

    @PostMapping
    public CodingSessionView create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Validated @RequestBody CodingPlanRequest request) {
        return coding.create(owners.requireOwner(authorization), request);
    }

    @GetMapping
    public List<CodingSessionView> list(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return coding.list(owners.requireOwner(authorization));
    }

    @GetMapping("/{id}")
    public CodingSessionView get(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return coding.get(owners.requireOwner(authorization), id);
    }

    /** 只跑可见示例，不写入历史（前端「运行」）。 */
    @PostMapping("/{id}/questions/{questionId}/run")
    public CodingSubmissionView run(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @PathVariable UUID questionId,
            @Valid @RequestBody CodingAnswerRequest request) {
        return coding.judgeAnswer(owners.requireOwner(authorization), id, questionId, request.answer(), true);
    }

    /** 跑全部用例（含隐藏）并落库（前端「提交」）。 */
    @PostMapping("/{id}/questions/{questionId}/submit")
    public CodingSubmissionView submit(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @PathVariable UUID questionId,
            @Valid @RequestBody CodingAnswerRequest request) {
        return coding.judgeAnswer(owners.requireOwner(authorization), id, questionId, request.answer(), false);
    }

    @GetMapping("/{id}/report")
    public CodingReportView report(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return coding.report(owners.requireOwner(authorization), id);
    }

    @DeleteMapping("/{id}")
    public void delete(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        coding.delete(owners.requireOwner(authorization), id);
    }
}
