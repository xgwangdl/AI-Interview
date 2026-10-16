package com.guangge.Interview.resume.dto;

import java.util.List;

/**
 * 生成结果，**不落库**：用户先看、先改，点保存才写进简历库。
 *
 * placeholders 是从正文里**重新抽出来的**，不采信模型的自述（沿用 §8.1 的做法）。
 */
public record ResumeDraftView(
        String rawText,
        List<String> questions,
        List<String> placeholders,
        String model) {
}
