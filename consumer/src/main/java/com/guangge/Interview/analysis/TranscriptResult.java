package com.guangge.Interview.analysis;

import java.util.List;

public record TranscriptResult(String text, List<Segment> segments) {

    public record Segment(double startSeconds, double endSeconds, String text) { }

    public static TranscriptResult ofText(String text) {
        return new TranscriptResult(text, List.of());
    }
}
