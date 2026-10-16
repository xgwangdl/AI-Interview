package com.guangge.Interview.practice.followup;

/** 追问供应商（真实实现走大模型，测试可替换）。 */
public interface PracticeFollowUpProvider {

    /**
     * 判断要不要就这道题再追问一次。
     *
     * @param context 原题、到目前为止的问答轮次、这是第几轮追问
     */
    PracticeFollowUpResult followUp(PracticeFollowUpContext context);

    String modelName();
}
