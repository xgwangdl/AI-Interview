package com.guangge.Interview.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.resume.dto.ResumeOptimizationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 简历优化输出的结构校验。 */
class ResumeOptimizationValidatorTest {

    private final ResumeOptimizationValidator validator =
            new ResumeOptimizationValidator(new ObjectMapper().findAndRegisterModules());

    @Test
    void acceptsAWellFormedResult() {
        ResumeOptimizationResult result = validator.parse(validJson());

        assertEquals(2, result.sections().size());
        assertEquals(List.of("【请补充：订单量级】"),
                ResumeOptimizationValidator.placeholdersIn(result.sections().get(1).optimized()));
    }

    @Test
    void stripsCodeFence() {
        assertEquals(2, validator.parse("```json\n" + validJson() + "\n```").sections().size());
    }

    @Test
    void rejectsEmptySections() {
        String json = validJson().replaceFirst("\"sections\": \\[[\\s\\S]*?\\],", "\"sections\": [],");
        assertThrows(ResumeFormatException.class, () -> validator.parse(json));
    }

    @Test
    void rejectsUnknownChangeType() {
        String json = validJson().replace("\"rephrase\"", "\"whatever\"");
        assertThrows(ResumeFormatException.class, () -> validator.parse(json));
    }

    @Test
    void rejectsFreeFormText() {
        assertThrows(ResumeFormatException.class,
                () -> validator.parse("这份简历整体不错，建议多写量化数据。"));
    }

    /**
     * 最要紧的一条：模型宣称"我没编造、留了占位符"，但正文里一个占位符都没有 ——
     * 那它很可能是直接编了数字。这种自相矛盾要拦下来。
     */
    @Test
    void rejectsDeclaredPlaceholdersThatDoNotAppearInTheText() {
        String json = validJson()
                .replace("\"optimized\": \"负责订单系统重构，服务拆分后接口耗时下降 40%【请补充：订单量级】\"",
                        "\"optimized\": \"负责订单系统重构，服务拆分后接口耗时下降 40%\"");
        assertThrows(ResumeFormatException.class, () -> validator.parse(json));
    }

    @Test
    void placeholdersAreReadFromTheTextNotFromTheModelClaim() {
        // 模型声明为空，但正文里确实有占位符：以正文为准（前端据此高亮）
        ResumeOptimizationResult result = validator.parse(validJson().replace(
                "\"placeholders\": [\"【请补充：订单量级】\"],", "\"placeholders\": [],"));

        assertTrue(result.placeholders().isEmpty());
        assertEquals(List.of("【请补充：订单量级】"),
                ResumeOptimizationValidator.placeholdersIn(result.sections().get(1).optimized()));
    }

    @Test
    void rejectsNumbersThatWereNotInTheOriginalResume() {
        ResumeOptimizationResult result = new ResumeOptimizationResult(
                "补充量化结果",
                List.of(new ResumeOptimizationResult.Section(
                        "project", "我优化了订单接口。", "我优化了订单接口，性能提升 25%。",
                        "补充结果", "add_detail")),
                List.of(),
                Map.of());

        ResumeFormatException exception = assertThrows(ResumeFormatException.class,
                () -> validator.validate(result, "我优化了订单接口。"));

        assertTrue(exception.getMessage().contains("25"));
        assertTrue(exception.getMessage().contains("不要由模型编写"));
    }

    @Test
    void allowsExistingNumbersAndNumbersInsidePlaceholders() {
        ResumeOptimizationResult result = new ResumeOptimizationResult(
                "保留原文数据",
                List.of(new ResumeOptimizationResult.Section(
                        "project", "接口从 900ms 优化到 100ms。",
                        "接口从 900ms 优化到 100ms，【请补充：2026 年数据】",
                        "保留结果并留占位符", "rephrase")),
                List.of("【请补充：2026 年数据】"),
                Map.of());

        ResumeOptimizationResult validated = validator.validate(result, "接口从 900ms 优化到 100ms。");

        assertEquals(1, validated.sections().size());
    }

    @Test
    void rejectsSectionWithoutOptimizedText() {
        String json = validJson().replace(
                "\"optimized\": \"负责订单系统重构，服务拆分后接口耗时下降 40%【请补充：订单量级】\"",
                "\"optimized\": \"\"");
        assertThrows(ResumeFormatException.class, () -> validator.parse(json));
    }

    static String validJson() {
        return """
                {
                  "summary": "缺少量化数据，项目描述偏流水账",
                  "sections": [
                    {
                      "kind": "work",
                      "original": "在某某公司负责订单系统开发。",
                      "optimized": "负责订单系统重构，主导服务拆分与性能优化（8 人团队）。",
                      "reason": "把职责改成动作+对象，突出主导角色",
                      "changeType": "rephrase"
                    },
                    {
                      "kind": "project",
                      "original": "订单系统优化项目。",
                      "optimized": "负责订单系统重构，服务拆分后接口耗时下降 40%【请补充：订单量级】",
                      "reason": "补齐 STAR 的结果部分，但量级数据原文没有，留给用户填",
                      "changeType": "add_detail"
                    }
                  ],
                  "placeholders": ["【请补充：订单量级】"],
                  "keywordCoverage": { "matched": ["订单系统"], "missing": ["分布式事务"] }
                }
                """;
    }
}
