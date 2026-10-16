package com.guangge.Interview.practice.followup;

/** 追问供应商（真实实现走大模型，测试可替换）。 */
public interface PracticeFollowUpProvider {

    PracticeFollowUpResult followUp(String role, String jobDescription, String question, String answer);

    String modelName();
}
