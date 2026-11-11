package com.guangge.Interview.jobradar;

import java.util.List;

public record DebriefParseResult(
        List<DebriefQuestion> questions,
        List<String> interviewerFocus,
        String summary) {

    public record DebriefQuestion(String text, String category, boolean hard, String note) { }
}
