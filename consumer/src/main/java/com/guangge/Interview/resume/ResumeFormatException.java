package com.guangge.Interview.resume;

/** 模型输出不符合约定结构。 */
public class ResumeFormatException extends RuntimeException {

    public ResumeFormatException(String message) {
        super(message);
    }

    public ResumeFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
