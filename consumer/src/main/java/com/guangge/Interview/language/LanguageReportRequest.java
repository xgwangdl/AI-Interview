package com.guangge.Interview.language;

import java.util.List;

/** 结束报告需要的上下文：全部轮次（只要说出来的部分）。 */
public record LanguageReportRequest(
        LanguageSpec language,
        LanguageScenario scenario,
        String level,
        String target,
        List<Turn> turns) {

    public record Turn(int index, String transcript, String reply) { }
}
