package com.guangge.Interview.language;

import java.util.List;

/** 一次"该回什么"需要的全部上下文（角色、水平、前面几轮、这一轮说的话）。 */
public record LanguageCoachRequest(
        LanguageSpec language,
        LanguageScenario scenario,
        String level,
        String target,
        List<Exchange> history,
        String transcript) {

    public record Exchange(String user, String assistant) { }
}
