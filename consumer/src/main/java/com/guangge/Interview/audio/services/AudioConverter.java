package com.guangge.Interview.audio.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * FFmpeg 音频抽取。
 *
 * 默认使用 PATH 中的 {@code ffmpeg}，也允许通过 {@code FFMPEG_PATH} 指定绝对路径。
 * 不再假设 Windows 上一定安装在某个固定目录。
 */
@Component
public class AudioConverter {

    private static final Logger log = LoggerFactory.getLogger(AudioConverter.class);
    private static final int MAX_ERROR_BYTES = 8192;
    private static final int MAX_ERROR_CHARS = 600;

    private final String ffmpegPath;
    private final long timeoutSeconds;
    private volatile Boolean available;

    public AudioConverter(
            @Value("${ffmpeg.path:ffmpeg}") String ffmpegPath,
            @Value("${ffmpeg.timeout-seconds:60}") long timeoutSeconds) {
        this.ffmpegPath = ffmpegPath == null || ffmpegPath.isBlank() ? "ffmpeg" : ffmpegPath.trim();
        this.timeoutSeconds = Math.max(1L, timeoutSeconds);
    }

    public File convertToWav(File inputFile) throws IOException, InterruptedException {
        File outputFile = File.createTempFile("converted-", ".wav");
        outputFile.deleteOnExit();

        ProcessBuilder processBuilder = new ProcessBuilder(
                ffmpegPath,
                "-y",
                "-i", inputFile.getAbsolutePath(),
                "-ar", "16000",
                "-ac", "1",
                "-sample_fmt", "s16",
                "-fflags", "+genpts",
                "-avoid_negative_ts", "make_zero",
                outputFile.getAbsolutePath()
        );

        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException exception) {
            throw new IOException("FFmpeg 不可用（" + ffmpegPath
                    + "）。请安装 ffmpeg，或在 .env 中设置 FFMPEG_PATH。", exception);
        }

        AtomicReference<String> stderr = new AtomicReference<>("");
        Thread outputThread = drainThread(process.getInputStream(), null);
        Thread errorThread = drainThread(process.getErrorStream(), stderr);
        outputThread.start();
        errorThread.start();

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
            throw new IOException("FFmpeg 处理超时（" + timeoutSeconds + " 秒）");
        }

        outputThread.join();
        errorThread.join();

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            log.warn("FFmpeg 转换失败 exit={} stderr={}", exitCode, stderr.get());
            throw new IOException("FFmpeg 转换失败，退出码 " + exitCode
                    + errorSuffix(stderr.get()));
        }
        if (outputFile.length() == 0) {
            throw new IOException("FFmpeg 没有生成可用的音频，请检查录像是否完整");
        }
        return outputFile;
    }

    /** 供根路径和诊断接口使用；结果缓存，避免每次请求都启动子进程。 */
    public boolean isAvailable() {
        Boolean cached = available;
        if (cached != null) return cached;
        synchronized (this) {
            if (available != null) return available;
            Process process = null;
            try {
                process = new ProcessBuilder(ffmpegPath, "-version")
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();
                boolean finished = process.waitFor(3, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    available = false;
                } else {
                    available = process.exitValue() == 0;
                }
            } catch (IOException exception) {
                available = false;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                available = false;
            } finally {
                if (process != null && process.isAlive()) process.destroyForcibly();
            }
            return available;
        }
    }

    String executable() {
        return ffmpegPath;
    }

    private static Thread drainThread(InputStream input, AtomicReference<String> captured) {
        return new Thread(() -> {
            try (input) {
                if (captured == null) {
                    input.transferTo(OutputStream.nullOutputStream());
                    return;
                }
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[1024];
                int read;
                while ((read = input.read(chunk)) != -1) {
                    if (buffer.size() < MAX_ERROR_BYTES) {
                        buffer.write(chunk, 0, Math.min(read, MAX_ERROR_BYTES - buffer.size()));
                    }
                }
                captured.set(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
            } catch (IOException exception) {
                if (captured != null) captured.set(exception.getMessage());
            }
        }, captured == null ? "ffmpeg-stdout" : "ffmpeg-stderr");
    }

    private static String errorSuffix(String stderr) {
        if (stderr == null || stderr.isBlank()) return "";
        String normalized = stderr.replaceAll("\\s+", " ").trim();
        String shortened = normalized.length() <= MAX_ERROR_CHARS
                ? normalized
                : normalized.substring(0, MAX_ERROR_CHARS) + "…";
        return "：" + shortened;
    }
}
