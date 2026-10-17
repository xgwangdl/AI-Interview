package com.guangge.Interview.language;

public interface LanguageCoach {

    LanguageCoachResult reply(LanguageCoachRequest request);

    LanguageReportResult report(LanguageReportRequest request);

    String modelName();
}
