package com.guangge.Interview.jobradar;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record DebriefCreateRequest(
        UUID jobLeadId,
        UUID practiceSessionId,
        @NotNull LocalDate interviewDate,
        @NotBlank @Size(max = 30) String roundName,
        @Pattern(regexp = "pending|pass|reject|unknown") String outcome,
        @NotBlank @Size(min = 10, max = 20000) String rawText,
        @Size(max = 200) String interviewerFocus,
        @Size(max = 5000) String selfNotes,
        boolean privacyAcknowledged) { }
