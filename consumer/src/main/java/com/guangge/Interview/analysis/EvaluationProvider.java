package com.guangge.Interview.analysis;

public interface EvaluationProvider {

    EvaluationOutcome evaluate(EvaluationRequest request);

    EvaluationProvenance provenance();
}
