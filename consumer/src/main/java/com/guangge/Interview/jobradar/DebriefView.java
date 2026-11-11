package com.guangge.Interview.jobradar;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DebriefView(
        UUID id,
        UUID jobLeadId,
        UUID practiceSessionId,
        LocalDate interviewDate,
        String roundName,
        String outcome,
        String parseStatus,
        List<DebriefQuestionView> questions,
        List<String> interviewerFocus,
        List<WeakPointView> weakPoints,
        int rawTextLength,
        Instant privacyAcknowledgedAt,
        Instant createdAt,
        Instant updatedAt) { }
