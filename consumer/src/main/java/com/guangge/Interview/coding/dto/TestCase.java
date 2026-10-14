package com.guangge.Interview.coding.dto;

/**
 * 一个测试用例。
 *
 * hidden=true 的用例只在判分时执行，前端只能看到「第 N 个用例未通过」，看不到输入与期望输出。
 */
public record TestCase(
        String name,
        String stdin,
        String expectedStdout,
        boolean hidden) {
}
