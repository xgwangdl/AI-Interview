package com.guangge.Interview.practice.rewrite;

public interface AnswerRewriteProvider {
    String rewrite(String role, String jobDescription, String question, String transcript);

    String modelName();
}
