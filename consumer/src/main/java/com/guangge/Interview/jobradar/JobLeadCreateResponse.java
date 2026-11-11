package com.guangge.Interview.jobradar;

import java.util.UUID;

public record JobLeadCreateResponse(UUID leadId, UUID runId, String status, boolean duplicated) { }
