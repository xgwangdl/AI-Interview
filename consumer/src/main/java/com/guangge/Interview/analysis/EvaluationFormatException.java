package com.guangge.Interview.analysis;

/** Raised when a model response does not satisfy the fixed evaluation schema. */
public class EvaluationFormatException extends RuntimeException {

    public EvaluationFormatException(String message) {
        super(message);
    }

    public EvaluationFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
