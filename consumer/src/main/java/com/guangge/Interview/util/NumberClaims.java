package com.guangge.Interview.util;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 提取文本中的阿拉伯数字，用于阻止模型新增用户没有提供的量化事实。 */
public final class NumberClaims {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final Pattern BRACKETED_PLACEHOLDER = Pattern.compile("【[^】]{1,80}】");

    private NumberClaims() {
    }

    public static Set<String> arabic(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value == null || value.isBlank()) return result;
        Matcher matcher = NUMBER.matcher(normalizeDigits(value));
        while (matcher.find()) result.add(matcher.group());
        return result;
    }

    public static Set<String> unsupported(String generated, String grounded) {
        Set<String> allowed = arabic(grounded);
        Set<String> result = new LinkedHashSet<>(arabic(generated));
        result.removeAll(allowed);
        return result;
    }

    public static String withoutBracketedPlaceholders(String value) {
        return value == null ? "" : BRACKETED_PLACEHOLDER.matcher(value).replaceAll("");
    }

    private static String normalizeDigits(String value) {
        StringBuilder normalized = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char code = value.charAt(index);
            if (code >= '０' && code <= '９') {
                normalized.append((char) ('0' + (code - '０')));
            } else {
                normalized.append(code);
            }
        }
        return normalized.toString();
    }
}
