package com.guangge.Interview.coding;

import java.util.Arrays;
import java.util.Optional;

/**
 * 支持的上机语言。
 *
 * 镜像选型偏「小」：Java 用 alpine 版 JDK、Python/Node 用 slim/alpine，
 * 只有 C++（gcc 官方镜像没有 alpine 变体）和 Go 偏大，第一次执行时会拉取。
 */
public enum CodeLanguage {

    JAVA("java", "eclipse-temurin:21-jdk-alpine", "Main.java",
            "javac -encoding UTF-8 Main.java", "java -Xmx192m Main"),

    PYTHON("python", "python:3.12-slim", "main.py",
            null, "python3 main.py"),

    CPP("cpp", "gcc:13", "main.cpp",
            "g++ -O2 -std=c++17 -o main main.cpp", "./main"),

    JAVASCRIPT("javascript", "node:20-alpine", "main.js",
            null, "node main.js"),

    GO("go", "golang:1.22-alpine", "main.go",
            "go build -o main main.go", "./main");

    private final String id;
    private final String image;
    private final String fileName;
    private final String compileCommand;
    private final String runCommand;

    CodeLanguage(String id, String image, String fileName, String compileCommand, String runCommand) {
        this.id = id;
        this.image = image;
        this.fileName = fileName;
        this.compileCommand = compileCommand;
        this.runCommand = runCommand;
    }

    public String id() { return id; }
    public String image() { return image; }
    public String fileName() { return fileName; }
    public String compileCommand() { return compileCommand; }
    public String runCommand() { return runCommand; }

    public boolean needsCompile() { return compileCommand != null; }

    public static Optional<CodeLanguage> fromId(String id) {
        if (id == null) return Optional.empty();
        String normalized = id.trim().toLowerCase();
        return Arrays.stream(values()).filter(item -> item.id.equals(normalized)).findFirst();
    }
}
