package com.guangge.Interview.practice.dto;

/**
 * 追问结果。
 *
 * question 为空表示"这次不追问"（回答已经够完整，或者转写太短没得问），
 * 此时前端照常进入下一题 —— 追问失败不该挡住练习。
 *
 * {@code round} 是这次追问在同一道原题下的序号（1 起）：前端据此说明
 * "面试官又追问了一句"，而不是让用户以为系统把同一道题问了两遍。
 */
public record PracticeFollowUpView(
        PracticeQuestion question,
        String reason,
        String model,
        int round) {

    public static PracticeFollowUpView none(String reason) {
        return new PracticeFollowUpView(null, reason, null, 0);
    }
}
