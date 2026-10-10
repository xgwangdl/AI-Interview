package com.guangge.Interview.util;

import java.util.regex.Pattern;

/**
 * 日志脱敏工具。
 *
 * 面试系统会处理简历、回答转写、联系方式等个人数据，任何一条日志都不应该把它们带出去。
 * 需要记录外部输入（异常信息、第三方返回、请求片段）时先过一遍 {@link #sanitize(String)}。
 */
public final class LogSanitizer {

    public static final String REDACTED = "***";
    public static final int MAX_LENGTH = 200;

    private static final Pattern BEARER_TOKEN =
            Pattern.compile("(?i)\\b(bearer)\\s+[A-Za-z0-9._~+/=-]{4,}");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(api[-_]?key|access[-_]?key|secret[-_]?key|secret|password|passwd|token)"
                    + "\\b\\s*[=:]\\s*\\S+");
    private static final Pattern EMAIL = Pattern.compile("\\b[\\w.+-]+@[\\w-]+\\.[\\w.-]+\\b");
    private static final Pattern CN_MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    private LogSanitizer() { }

    /** 只做脱敏，不截断。需要保留更长文本时用它，再自行调用 {@link #truncate(String, int)}。 */
    public static String redact(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        // Bearer 必须先处理：否则通用键值规则会先吃掉 "Authorization: Bearer"，把令牌留在后面。
        String result = BEARER_TOKEN.matcher(value).replaceAll("$1 " + REDACTED);
        result = SECRET_ASSIGNMENT.matcher(result).replaceAll("$1=" + REDACTED);
        result = EMAIL.matcher(result).replaceAll(REDACTED);
        return CN_MOBILE.matcher(result).replaceAll(REDACTED);
    }

    public static String sanitize(String value) {
        return truncate(redact(value), MAX_LENGTH);
    }

    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (maxLength < 1 || value.length() <= maxLength) {
            return maxLength < 1 ? "" : value;
        }
        return value.substring(0, maxLength) + "...(" + (value.length() - maxLength) + " more chars)";
    }
}
