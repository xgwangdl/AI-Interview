package com.guangge.Interview.language;

import java.util.List;

public record LanguageReportResult(
        Integer fluency,
        Integer vocabulary,
        Integer grammar,
        Integer task,
        String summary,
        List<String> improvements) {
}
