package com.guangge.Interview.resume;

import com.guangge.Interview.resume.dto.ResumeData;

import java.util.List;

public final class ResumeDataFixtures {

    private ResumeDataFixtures() { }

    public static ResumeData sample(String language, String documentType) {
        return new ResumeData(
                language,
                documentType,
                "2026-11",
                "Java 后端工程师",
                new ResumeData.Person(
                        "张伟",
                        null,
                        null,
                        "13800000000",
                        "zhangwei@example.com",
                        List.of(new ResumeData.Link("GitHub", "https://github.com/zhangwei"))),
                switch (language) {
                    case "en" -> "Java Backend Engineer";
                    case "ja" -> "Javaバックエンドエンジニア";
                    default -> "Java 后端工程师";
                },
                List.of("专注订单系统与性能优化。"),
                List.of(new ResumeData.SkillGroup(
                        "核心技术", List.of("Java", "Spring Boot", "MySQL", "Redis"))),
                List.of(new ResumeData.Experience(
                        "2020.07",
                        "至今",
                        "某某科技",
                        "Java 后端工程师",
                        List.of("负责订单系统重构，接口耗时从 800ms 降至 120ms。"))),
                List.of(new ResumeData.Education(
                        "2016.09", "2020.06", "某某大学", "计算机科学与技术")),
                List.of(new ResumeData.Project(
                        "2025.01", "2025.06", "订单查询优化", "后端负责人",
                        List.of("合并重复查询并增加联合索引。"))),
                List.of(new ResumeData.PortfolioItem(
                        "个人项目", "开源面试练习系统", "https://github.com/zhangwei/project")),
                List.of(new ResumeData.Certification("软件设计师", null, "2021")),
                List.of(new ResumeData.LanguageSkill("英语", "CET-6")),
                List.of("未提供当前住址"));
    }
}
