package com.guangge.Interview.coding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 在本机 Docker 里执行用户代码。
 *
 * 安全约束（因为这些参数就是唯一的安全边界，改动前请想清楚）：
 * <ul>
 *   <li>{@code --network none}：禁止联网（防外联、防挖矿）</li>
 *   <li>{@code --memory} + {@code --memory-swap}：内存上限，且不允许用 swap 绕过</li>
 *   <li>{@code --cpus 0.5} + {@code --pids-limit 64}：防 fork 炸弹与 CPU 抢占</li>
 *   <li>{@code --read-only} + {@code --tmpfs /tmp}：根文件系统只读，只开放 /tmp</li>
 *   <li>只把当次运行的临时目录挂到 /work，进程结束即删除</li>
 *   <li>shell 里 {@code ulimit -f}：限制单文件大小，防写爆磁盘</li>
 *   <li>整体超时后 {@code docker kill}，避免只杀掉客户端进程而容器仍在跑</li>
 * </ul>
 *
 * 编译/运行结果写进挂载目录里的文件，而不是通过管道读取——管道缓冲区写满会把
 * 子进程卡死（经典死锁），写文件既安全又能保留超时前的部分输出。
 */
@Service
public class CodeRunnerService {

    private static final Logger log = LoggerFactory.getLogger(CodeRunnerService.class);
    private static final int MAX_OUTPUT_CHARS = 8 * 1024;
    private static final long ACQUIRE_TIMEOUT_SECONDS = 5;

    private final boolean enabled;
    private final int maxConcurrency;
    private final Duration compileTimeout;
    private final Duration runTimeout;
    private final long memoryMb;
    private final String dockerExecutable;
    private final Semaphore slots;

    public CodeRunnerService(
            @Value("${practice.code-runner.enabled:false}") boolean enabled,
            @Value("${practice.code-runner.max-concurrency:2}") int maxConcurrency,
            @Value("${practice.code-runner.compile-timeout-seconds:10}") int compileTimeoutSeconds,
            @Value("${practice.code-runner.run-timeout-seconds:5}") int runTimeoutSeconds,
            @Value("${practice.code-runner.memory-mb:256}") long memoryMb,
            @Value("${practice.code-runner.docker:docker}") String dockerExecutable) {
        this.enabled = enabled;
        this.maxConcurrency = Math.max(1, maxConcurrency);
        this.compileTimeout = Duration.ofSeconds(Math.max(1, compileTimeoutSeconds));
        this.runTimeout = Duration.ofSeconds(Math.max(1, runTimeoutSeconds));
        this.memoryMb = Math.max(64, memoryMb);
        this.dockerExecutable = dockerExecutable;
        this.slots = new Semaphore(this.maxConcurrency, true);
    }

    public boolean enabled() {
        return enabled;
    }

    public int maxConcurrency() {
        return maxConcurrency;
    }

    /**
     * 执行一段源码。
     *
     * @param stdin 程序标准输入（用例的输入），可为空
     */
    public CodeRunResult run(CodeLanguage language, String source, String stdin) {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "代码执行未启用（practice.code-runner.enabled=false）");
        }
        acquireSlot();
        Path workDir = null;
        String containerName = "light-interview-run-" + UUID.randomUUID();
        try {
            workDir = Files.createTempDirectory("code-run-");
            Files.writeString(workDir.resolve(language.fileName()),
                    source == null ? "" : source, StandardCharsets.UTF_8);

            long startedAt = System.currentTimeMillis();
            Process process = new ProcessBuilder(dockerCommand(language, workDir, containerName)).start();
            feedStdin(process, stdin);
            boolean finished = process.waitFor(overallTimeoutMillis(), TimeUnit.MILLISECONDS);
            long elapsed = System.currentTimeMillis() - startedAt;

            if (!finished) {
                log.warn("代码执行超时，终止容器 {}（语言 {}）", containerName, language.id());
                killContainer(containerName);
                process.destroyForcibly();
                return CodeRunResult.timeout(elapsed, readFile(workDir.resolve("run.out")));
            }
            return collect(workDir, process.exitValue(), elapsed);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "代码执行失败：" + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "代码执行被中断", exception);
        } finally {
            if (workDir != null) deleteQuietly(workDir);
            slots.release();
        }
    }

    private void acquireSlot() {
        try {
            if (!slots.tryAcquire(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "代码执行排队中，请稍后再试（最多并发 " + maxConcurrency + "）");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "等待执行槽位被中断", exception);
        }
    }

    private List<String> dockerCommand(CodeLanguage language, Path workDir, String containerName) {
        List<String> command = new ArrayList<>(List.of(
                dockerExecutable, "run", "--rm", "-i",
                "--name", containerName,
                "--network", "none",
                "--memory", memoryMb + "m",
                "--memory-swap", memoryMb + "m",
                "--cpus", "0.5",
                "--pids-limit", "64",
                "--read-only",
                "--tmpfs", "/tmp:rw,size=64m",
                "-v", workDir.toAbsolutePath() + ":/work",
                "-w", "/work",
                language.image(),
                "sh", "-c", script(language)));
        return command;
    }

    /** 编译与运行都写文件，超时或崩溃时仍能读到已经产生的输出。 */
    private String script(CodeLanguage language) {
        StringBuilder script = new StringBuilder();
        script.append("cd /work\n");
        script.append("export HOME=/tmp\n");          // 有些工具需要可写的 HOME
        script.append("export GOCACHE=/tmp/go-cache\n"); // Go 需要可写的构建缓存
        script.append("ulimit -f 4096\n");            // 单文件上限 2MB（512B 块 × 4096）
        String compileLimit = String.valueOf(compileTimeout.toSeconds());
        String runLimit = String.valueOf(runTimeout.toSeconds());
        if (language.needsCompile()) {
            // 用 timeout 分别限制编译与运行：只靠容器级总超时会让「运行 5 秒」形同虚设，
            // 一个死循环能一直耗到总超时才被杀掉。
            script.append("timeout ").append(compileLimit).append(' ')
                    .append(language.compileCommand()).append(" > compile.out 2> compile.err\n");
            script.append("compile_code=$?\n");
            script.append("echo $compile_code > compile.exit\n");
            script.append("if [ $compile_code -eq 0 ]; then\n");
            script.append("  timeout ").append(runLimit).append(' ')
                    .append(language.runCommand()).append(" > run.out 2> run.err\n");
            script.append("  echo $? > run.exit\n");
            script.append("fi\n");
        } else {
            script.append("timeout ").append(runLimit).append(' ')
                    .append(language.runCommand()).append(" > run.out 2> run.err\n");
            script.append("echo $? > run.exit\n");
        }
        return script.toString();
    }

    private CodeRunResult collect(Path workDir, int processExitCode, long elapsed) throws IOException {
        String stdout = readFile(workDir.resolve("run.out"));
        String stderr = readFile(workDir.resolve("run.err"));
        String compileStdout = readFile(workDir.resolve("compile.out"));
        String compileStderr = readFile(workDir.resolve("compile.err"));

        String compileOutput = compileStdout;
        if (!compileStderr.isBlank()) {
            compileOutput = compileOutput.isBlank() ? compileStderr : compileOutput + "\n" + compileStderr;
        }
        // run.exit 不存在说明编译没过（或容器压根没起来），退回到编译退出码/进程退出码。
        int exitCode = readInt(workDir.resolve("run.exit"),
                readInt(workDir.resolve("compile.exit"), processExitCode));
        // GNU/busybox timeout 超时返回 124。
        if (exitCode == 124) {
            return new CodeRunResult(false, exitCode, stdout, stderr, compileOutput, elapsed, true);
        }
        boolean ok = exitCode == 0;
        return new CodeRunResult(ok, exitCode, stdout, stderr, compileOutput, elapsed, false);
    }

    private void feedStdin(Process process, String stdin) throws IOException {
        try (OutputStream out = process.getOutputStream()) {
            if (stdin != null && !stdin.isEmpty()) {
                out.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        }
        // 关掉 stdin：否则读取输入的程序会一直等下去，只能靠超时结束。
    }

    private void killContainer(String containerName) {
        try {
            Process kill = new ProcessBuilder(dockerExecutable, "kill", containerName)
                    .redirectErrorStream(true).start();
            kill.waitFor(5, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("终止容器 {} 失败：{}", containerName, exception.getMessage());
        }
    }

    private long overallTimeoutMillis() {
        // 编译 + 运行之外再留一点容器启动与收尾的时间。
        return compileTimeout.plus(runTimeout).plusSeconds(5).toMillis();
    }

    private String readFile(Path path) {
        try {
            if (!Files.isRegularFile(path)) return "";
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return content.length() > MAX_OUTPUT_CHARS ? content.substring(0, MAX_OUTPUT_CHARS) + "\n…（输出过长已截断）" : content;
        } catch (IOException exception) {
            return "";
        }
    }

    private int readInt(Path path, int fallback) {
        try {
            if (!Files.isRegularFile(path)) return fallback;
            return Integer.parseInt(Files.readString(path, StandardCharsets.UTF_8).trim());
        } catch (IOException | NumberFormatException exception) {
            return fallback;
        }
    }

    private void deleteQuietly(Path dir) {
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时目录清理失败不影响结果。
                }
            });
        } catch (IOException exception) {
            log.debug("清理临时目录 {} 失败：{}", dir, exception.getMessage());
        }
    }
}
