package com.guangge.Interview.util;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 提取文本中的数字/量化表述，用于阻止模型新增用户没有提供的量化事实。 */
public final class NumberClaims {

    private static final String UNITS = "ms|s|秒|分钟|小时|天|周|个月|月|年|次|人|个|条|台|套|万元|元|块|倍|%";
    private static final Pattern ARABIC_CLAIM = Pattern.compile(
            "\\d+(?:\\.\\d+)?(?:\\s*(?:" + UNITS + "))?");
    private static final Pattern CHINESE_CLAIM = Pattern.compile(
            "([零〇一二两三四五六七八九十百千万亿]+)\\s*(" + UNITS + ")");
    private static final Pattern CHINESE_PERCENT = Pattern.compile(
            "百分之([零〇一二两三四五六七八九十百千万亿]+)");
    private static final Pattern BRACKETED_PLACEHOLDER = Pattern.compile("【[^】]{1,80}】");

    private NumberClaims() {
    }

    public static Set<String> claims(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value == null || value.isBlank()) return result;
        String normalized = normalizeDigits(value);
        Matcher matcher = ARABIC_CLAIM.matcher(normalized);
        while (matcher.find()) result.add(matcher.group().replaceAll("\\s+", ""));
        Matcher percent = CHINESE_PERCENT.matcher(normalized);
        while (percent.find()) {
            result.add(parseChineseNumber(percent.group(1)) + "%");
        }
        Matcher chinese = CHINESE_CLAIM.matcher(normalized);
        while (chinese.find()) {
            result.add(parseChineseNumber(chinese.group(1)) + normalizeUnit(chinese.group(2)));
        }
        return result;
    }

    public static Set<String> unsupported(String generated, String grounded) {
        Set<String> allowed = claims(grounded);
        Set<String> result = new LinkedHashSet<>(claims(generated));
        result.removeAll(allowed);
        return result;
    }

    public static Set<String> arabic(String value) {
        return claims(value);
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

    private static String normalizeUnit(String unit) {
        return "％".equals(unit) ? "%" : unit;
    }

    static int parseChineseNumber(String value) {
        int total = 0;
        int section = 0;
        int number = 0;
        for (int index = 0; index < value.length(); index++) {
            int digit = chineseDigit(value.charAt(index));
            if (digit >= 0) {
                number = digit;
                continue;
            }
            int unit = chineseUnit(value.charAt(index));
            if (unit == 10_000 || unit == 100_000_000) {
                section += number;
                total += (section == 0 ? 1 : section) * unit;
                section = 0;
                number = 0;
            } else if (unit > 0) {
                section += (number == 0 ? 1 : number) * unit;
                number = 0;
            }
        }
        return total + section + number;
    }

    private static int chineseDigit(char value) {
        return switch (value) {
            case '零', '〇' -> 0;
            case '一' -> 1;
            case '二', '两' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> -1;
        };
    }

    private static int chineseUnit(char value) {
        return switch (value) {
            case '十' -> 10;
            case '百' -> 100;
            case '千' -> 1_000;
            case '万' -> 10_000;
            case '亿' -> 100_000_000;
            default -> 0;
        };
    }
}
