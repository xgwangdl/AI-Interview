package com.guangge.Interview.language;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 会话详情：开场白、已经发生的轮次、结束后的报告。 */
public record LanguageSessionView(
        UUID id,
        String language,
        /** 语言的显示名与浏览器语音标签：开场白在第一条回复之前就要用它们说话。 */
        String languageName,
        String browserVoice,
        boolean serverVoice,
        String scenario,
        String level,
        String target,
        String status,
        int turnCount,
        int maxTurns,
        Instant createdAt,
        Instant finishedAt,
        Instant retentionUntil,
        /** 场景的固定开场白（第一句不调用模型）。 */
        String opening,
        List<LanguageTurnView> turns,
        LanguageReportView report) {
}
