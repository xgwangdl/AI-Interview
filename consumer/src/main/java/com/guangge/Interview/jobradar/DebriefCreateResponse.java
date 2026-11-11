package com.guangge.Interview.jobradar;

import java.util.UUID;

public record DebriefCreateResponse(UUID debriefId, UUID runId, String parseStatus) { }
