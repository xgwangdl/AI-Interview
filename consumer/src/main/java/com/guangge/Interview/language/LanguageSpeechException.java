package com.guangge.Interview.language;

/** 语音识别/合成失败：Service 会把它转成"这一轮没听清，再说一次"这种可读提示。 */
public class LanguageSpeechException extends RuntimeException {
    public LanguageSpeechException(String message) {
        super(message);
    }

    public LanguageSpeechException(String message, Throwable cause) {
        super(message, cause);
    }
}
