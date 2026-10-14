package com.guangge.Interview.coding;

/**
 * 一次沙箱执行的结果。
 *
 * compileOutput 与 stderr 分开，前端才能把「编译不过」和「运行时报错」讲清楚。
 */
public record CodeRunResult(
        boolean ok,
        int exitCode,
        String stdout,
        String stderr,
        String compileOutput,
        long durationMs,
        boolean timedOut) {

    public static CodeRunResult timeout(long durationMs, String partialStdout) {
        return new CodeRunResult(false, -1, partialStdout, "执行超时，已终止", "", durationMs, true);
    }
}
