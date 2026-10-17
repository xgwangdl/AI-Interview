package com.guangge.Interview.language;

import java.time.Instant;
import java.util.List;

/**
 * 结束报告。
 *
 * 只有四项（流利度 / 用词 / 语法 / 任务完成度）：**没有发音分**。
 * 现有识别只给文本，硬编一个"发音 87 分"是没有依据的数字（见需求文档 §2.3）。
 */
public record LanguageReportView(
        Integer fluency,
        Integer vocabulary,
        Integer grammar,
        Integer task,
        String summary,
        List<String> improvements,
        Instant generatedAt) {
}
