package com.guangge.Interview.resume.dto;

import java.util.List;
import java.util.Map;

/**
 * 模型必须输出的固定结构。
 *
 * 分数、结构一律走校验：自由文本、缺字段、编造数字都要在落库前拦下来
 * （沿用 EvaluationResultValidator 的做法）。
 */
public record ResumeOptimizationResult(
        String summary,
        List<Section> sections,
        List<String> placeholders,
        Map<String, List<String>> keywordCoverage) {

    /** 一段的改动。original 可能为空（新增段落）。 */
    public record Section(
            String kind,
            String original,
            String optimized,
            String reason,
            String changeType) {

        /** 允许的改动类型；模型给了别的值就判为格式错误。 */
        public static final List<String> CHANGE_TYPES =
                List.of("rephrase", "restructure", "add_detail", "remove", "keep");
    }
}
