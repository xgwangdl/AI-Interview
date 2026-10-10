package com.guangge.Interview.analysis;

/**
 * 评分供应商的原始输出与解析结果。
 *
 * 报告要求保留「原始模型响应」，所以供应商必须把原文一并交出来，而不是只给解析后的结构。
 */
public record EvaluationOutcome(EvaluationResult result, String rawResponse) { }
