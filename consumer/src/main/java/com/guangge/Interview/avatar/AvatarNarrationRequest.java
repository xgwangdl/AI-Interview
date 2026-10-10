package com.guangge.Interview.avatar;

import java.util.UUID;

public record AvatarNarrationRequest(
        UUID sessionId,
        int questionIndex,
        String questionText,
        String interviewerStyle,
        String role) { }
