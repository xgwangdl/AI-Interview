package com.guangge.Interview.resume.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 从零创建的入参（见 docs/RESUME-REQUIREMENTS.md §4.8）。
 *
 * 只收"最少必要的字段"：白手起家的人本来就不知道写什么，字段越少越填得下去。
 * 每段经历用同一个 {@link Entry}，前端就是同一张可增删的小卡片。
 */
public final class ResumeDraftRequests {

    /** 素材总量上限：够写一份简历，也不至于把上下文撑爆。 */
    public static final int MAX_ENTRIES = 12;
    public static final int MAX_FIELD = 600;

    private ResumeDraftRequests() { }

    /** 一段经历：学校 / 公司 / 项目都用这一张卡片。 */
    public record Entry(
            @Size(max = 200) String title,
            @Size(max = 200) String role,
            @Size(max = 100) String period,
            @Size(max = MAX_FIELD) String detail,
            @Size(max = MAX_FIELD) String result) {
    }

    public record Basics(
            @Size(max = 100) String name,
            @Size(max = 100) String phone,
            @Size(max = 200) String email,
            @Size(max = 100) String city,
            @Size(max = MAX_FIELD) String extra) {
    }

    /** 采集到的素材。列表可以为空 —— 空素材也要能生成一份带占位符的骨架。 */
    public record Draft(
            @Size(max = 200) String targetRole,
            @Valid Basics basics,
            @Valid @Size(max = MAX_ENTRIES) List<Entry> education,
            @Valid @Size(max = MAX_ENTRIES) List<Entry> work,
            @Valid @Size(max = MAX_ENTRIES) List<Entry> projects,
            @Size(max = MAX_FIELD) String skills,
            @Size(max = MAX_FIELD) String other) {
    }

    /**
     * 追问补全：现有正文 + 用户补充的信息。
     *
     * 刻意**不带简历 id**：成稿页在保存之前就要能"继续完善"，服务端也不需要读库
     * —— 与 `/draft` 一样是个纯粹的无状态生成接口。
     */
    public record Refine(
            @NotBlank @Size(max = 20000) String rawText,
            @NotBlank @Size(max = 4000) String supplement,
            /** 界面上显示过的追问，原样回传让模型知道该替换哪几处占位符。 */
            @Size(max = 3) List<@Size(max = 200) String> questions) {
    }
}
