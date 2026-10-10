package com.guangge.Interview.analysis;

/** Model and prompt identity captured with every stored evaluation. */
public record EvaluationProvenance(String modelName, String modelVersion, String promptVersion) { }
