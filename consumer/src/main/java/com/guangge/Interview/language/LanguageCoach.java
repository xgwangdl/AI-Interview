package com.guangge.Interview.language;

public interface LanguageCoach {

    LanguageCoachResult reply(LanguageCoachRequest request);

    LanguageReportResult report(LanguageReportRequest request);

    /** L2：流式生成回复（只要纯文本，不带 JSON），每个增量回调一次。 */
    String streamReply(LanguageCoachRequest request, java.util.function.Consumer<String> onDelta);

    /** L2：回复先播出去，反馈随后再来（只挑学习者原话里的问题）。 */
    java.util.List<LanguageFeedback> feedback(LanguageCoachRequest request);

    String modelName();
}
