package com.guangge.Interview.audio.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class AudioConverter {

    public static File convertToWav(File inputFile) throws IOException, InterruptedException {
        // 获取 FFmpeg 路径
        String ffmpegPath = getFFmpegPath();

        // 创建临时文件用于存储转换后的音频
        File outputFile = File.createTempFile("converted-", ".wav");
        outputFile.deleteOnExit();

        // 如果输出文件已存在，删除它
        if (outputFile.exists()) {
            outputFile.delete();
        }

        // 构建 FFmpeg 命令
        ProcessBuilder processBuilder = new ProcessBuilder(
                ffmpegPath,
                "-y", // 强制覆盖输出文件
                "-i", inputFile.getAbsolutePath(),
                "-ar", "16000",
                "-ac", "1",
                "-sample_fmt", "s16",
                "-fflags", "+genpts",      // 修复时间戳
                "-avoid_negative_ts", "make_zero",
                outputFile.getAbsolutePath()
        );

        // 启动进程
        Process process = processBuilder.start();

        // 使用多线程读取输出流和错误流
        Thread outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("FFmpeg output: " + line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        outputThread.start();

        Thread errorThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.err.println("FFmpeg error: " + line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        errorThread.start();

        // 设置超时时间并等待进程完成
        boolean finished = process.waitFor(ffmpegTimeoutSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroy(); // 强制结束进程
            throw new IOException("FFmpeg 处理超时");
        }

        // 等待线程结束
        outputThread.join();
        errorThread.join();

        // 检查退出码
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new IOException("FFmpeg 转换失败，退出码: " + exitCode);
        }

        return outputFile;
    }

    private static String getFFmpegPath() {
        String configured = System.getProperty("ffmpeg.path");
        if (configured == null || configured.isBlank()) configured = System.getenv("FFMPEG_PATH");
        if (configured != null && !configured.isBlank()) return configured;
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "C:\\software\\ffmpeg-7.1-full_build\\bin\\ffmpeg.exe"; // Windows 路径
        } else if (os.contains("linux") || os.contains("mac")) {
            return "/usr/bin/ffmpeg"; // Linux/macOS 路径
        } else {
            throw new UnsupportedOperationException("Unsupported operating system: " + os);
        }
    }

    private static long ffmpegTimeoutSeconds() {
        String configured = System.getProperty("ffmpeg.timeoutSeconds");
        if (configured == null || configured.isBlank()) configured = System.getenv("FFMPEG_TIMEOUT_SECONDS");
        if (configured == null || configured.isBlank()) return 60L;
        try {
            return Math.max(1L, Long.parseLong(configured.trim()));
        } catch (NumberFormatException ignored) {
            return 60L;
        }
    }
}
