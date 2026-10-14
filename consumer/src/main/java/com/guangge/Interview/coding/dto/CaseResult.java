package com.guangge.Interview.coding.dto;

/**
 * 单个用例的判分结果。
 *
 * 隐藏用例只回传 index/name/passed，不带输入输出，避免泄题。
 */
public record CaseResult(
        int index,
        String name,
        boolean passed,
        boolean hidden,
        long durationMs,
        String message,
        String input,
        String expected,
        String actual) {

    public static CaseResult hidden(int index, String name, boolean passed, long durationMs) {
        return new CaseResult(index, name, passed, true, durationMs, null, null, null, null);
    }
}
