package com.guangge.Interview.language;

/** 模型没按约定返回（或返回空），由 Service 转成用户能看懂的 502。 */
public class LanguageCoachException extends RuntimeException {
    public LanguageCoachException(String message) {
        super(message);
    }

    public LanguageCoachException(String message, Throwable cause) {
        super(message, cause);
    }
}
