package com.guangge.Interview.practice.followup;

/** 追问链路失败：模型返回不符合约定、或转写/调用出错。 */
public class PracticeFollowUpException extends RuntimeException {

    public PracticeFollowUpException(String message) {
        super(message);
    }

    public PracticeFollowUpException(String message, Throwable cause) {
        super(message, cause);
    }
}
