package com.guangge.Interview.jobradar;

import java.time.Instant;

public class JobRunDeferredException extends RuntimeException {
    private final Instant resumeAt;

    public JobRunDeferredException(Instant resumeAt, String message) {
        super(message);
        this.resumeAt = resumeAt;
    }

    public Instant resumeAt() {
        return resumeAt;
    }
}
