package com.guangge.Interview.analysis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 本地兜底评分，用于没有大模型凭证的开发与联调环境。
 *
 * 溯源里写明 stub-local，报告上能一眼看出分数不是真实模型给的。
 * 启用方式：practice.evaluation.provider=stub
 */
@Component
@ConditionalOnProperty(name = "practice.evaluation.provider", havingValue = "stub")
public class StubEvaluationProvider implements EvaluationProvider {

    private static final String MODEL = "stub-local";
    private static final String PROMPT_VERSION = "practice-evaluation-stub-v1";
    private static final String EVIDENCE_QUOTE = "本地模拟转写";

    @Override
    public EvaluationOutcome evaluate(EvaluationRequest request) {
        String raw = "{\"stub\":true,\"questionId\":\"" + request.questionId() + "\"}";
        EvaluationResult result = new EvaluationResult(
                75.0,
                new EvaluationResult.DimensionScores(78.0, 72.0, 75.0, 70.0, 76.0),
                new EvaluationResult.DeliveryMetrics(200.0, 3, 5.5, 2),
                List.of(
                        new EvaluationResult.DimensionFeedback(
                                "relevance", "（本地模拟）回答与题目基本相关。", EVIDENCE_QUOTE),
                        new EvaluationResult.DimensionFeedback(
                                "completeness", "（本地模拟）已覆盖主要要点。", EVIDENCE_QUOTE),
                        new EvaluationResult.DimensionFeedback(
                                "professionalism", "（本地模拟）表达较专业。", EVIDENCE_QUOTE),
                        new EvaluationResult.DimensionFeedback(
                                "starStructure", "（本地模拟）结构可以更清晰。", EVIDENCE_QUOTE),
                        new EvaluationResult.DimensionFeedback(
                                "logic", "（本地模拟）论证顺序基本合理。", EVIDENCE_QUOTE)),
                List.of("（本地模拟评分）接入真实模型后，这里会给出针对本题的改进建议。"),
                "（本地模拟评分）示例回答占位内容。",
                // 证据刻意放在非零时间点，便于验证报告页「点击证据跳转到视频位置」。
                List.of(new EvaluationResult.Evidence("relevance", EVIDENCE_QUOTE, 1.5, 2.5)));
        return new EvaluationOutcome(result, raw);
    }

    @Override
    public EvaluationProvenance provenance() {
        return new EvaluationProvenance(MODEL, MODEL, PROMPT_VERSION);
    }
}
