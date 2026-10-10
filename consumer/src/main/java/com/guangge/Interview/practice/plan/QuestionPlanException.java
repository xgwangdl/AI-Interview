package com.guangge.Interview.practice.plan;

/** 模型返回的题目不符合约定结构。 */
public class QuestionPlanException extends RuntimeException {

    public QuestionPlanException(String message) {
        super(message);
    }

    public QuestionPlanException(String message, Throwable cause) {
        super(message, cause);
    }
}
