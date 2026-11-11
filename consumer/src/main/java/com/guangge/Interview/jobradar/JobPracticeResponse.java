package com.guangge.Interview.jobradar;

import com.guangge.Interview.practice.dto.PracticeQuestion;

import java.util.List;
import java.util.UUID;

public record JobPracticeResponse(UUID sessionId, List<PracticeQuestion> questions, List<String> focus) { }
