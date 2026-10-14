package com.guangge.Interview.coding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 沙箱执行器的测试。
 *
 * 需要 Docker 与对应镜像的用例用 {@code -Dcode.runner.integration=true} 打开，
 * 默认不跑（CI 上没有 Docker daemon，跑了必然失败）。
 */
class CodeRunnerServiceTest {

    private static CodeRunnerService runner(int runSeconds, int compileSeconds) {
        return new CodeRunnerService(true, 2, compileSeconds, runSeconds, 256, "docker");
    }

    @Test
    void refusesToRunWhenDisabled() {
        CodeRunnerService disabled = new CodeRunnerService(false, 2, 10, 5, 256, "docker");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> disabled.run(CodeLanguage.PYTHON, "print(1)", ""));

        assertEquals(503, error.getStatusCode().value());
        assertFalse(disabled.enabled());
    }

    @Test
    void parsesLanguageIds() {
        assertEquals(CodeLanguage.JAVA, CodeLanguage.fromId("java").orElseThrow());
        assertEquals(CodeLanguage.CPP, CodeLanguage.fromId("CPP").orElseThrow());
        assertTrue(CodeLanguage.fromId("rust").isEmpty());
        assertTrue(CodeLanguage.JAVA.needsCompile());
        assertFalse(CodeLanguage.PYTHON.needsCompile());
    }

    @Test
    @EnabledIfSystemProperty(named = "code.runner.integration", matches = "true")
    void runsPythonAndFeedsStdin() {
        String source = """
                import sys
                name = sys.stdin.read().strip()
                print(f"hello {name}")
                """;

        CodeRunResult result = runner(5, 10).run(CodeLanguage.PYTHON, source, "world\n");

        assertTrue(result.ok(), () -> "stderr=" + result.stderr() + " compile=" + result.compileOutput());
        assertEquals("hello world", result.stdout().trim());
        assertEquals(0, result.exitCode());
        assertFalse(result.timedOut());
    }

    @Test
    @EnabledIfSystemProperty(named = "code.runner.integration", matches = "true")
    void runsJavaScript() {
        CodeRunResult result = runner(5, 10).run(CodeLanguage.JAVASCRIPT,
                "console.log([1,2,3].map(x => x * 2).join(','));\n", "");

        assertTrue(result.ok(), () -> "stderr=" + result.stderr());
        assertEquals("2,4,6", result.stdout().trim());
    }

    @Test
    @EnabledIfSystemProperty(named = "code.runner.integration", matches = "true")
    void reportsCompileErrorsSeparately() {
        CodeRunResult result = runner(5, 20).run(CodeLanguage.JAVA,
                "public class Main { public static void main(String[] a) { int x = \"oops\"; } }\n", "");

        assertFalse(result.ok());
        assertFalse(result.compileOutput().isBlank(), "编译错误应当回显给用户");
        assertTrue(result.compileOutput().contains("error") || result.compileOutput().contains("错误"),
                result.compileOutput());
    }

    @Test
    @EnabledIfSystemProperty(named = "code.runner.integration", matches = "true")
    void killsInfiniteLoops() {
        long startedAt = System.currentTimeMillis();

        CodeRunResult result = runner(2, 10).run(CodeLanguage.PYTHON,
                "while True:\n    pass\n", "");

        long elapsed = System.currentTimeMillis() - startedAt;
        assertTrue(result.timedOut(), "死循环必须被判定为超时");
        assertFalse(result.ok());
        assertTrue(elapsed < 20_000, "应当在秒级被终止，实际 " + elapsed + "ms");
    }

    @Test
    @EnabledIfSystemProperty(named = "code.runner.integration", matches = "true")
    void blocksNetworkAccess() {
        String source = """
                import socket
                try:
                    socket.create_connection(("1.1.1.1", 53), timeout=3)
                    print("CONNECTED")
                except Exception as exc:
                    print("BLOCKED")
                """;

        CodeRunResult result = runner(10, 10).run(CodeLanguage.PYTHON, source, "");

        assertTrue(result.ok(), () -> "stderr=" + result.stderr());
        assertEquals("BLOCKED", result.stdout().trim(), "沙箱必须禁止联网");
    }
}
