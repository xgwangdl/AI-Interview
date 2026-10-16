package com.guangge.Interview.practice.dto;

/**
 * 追问结果。
 *
 * question 为空表示"这次不追问"（回答已经够完整，或者转写太短没得问），
 * 此时前端照常进入下一题 —— 追问失败不该挡住练习。
 */
public record PracticeFollowUpView(
        PracticeQuestion question,
        String reason,
        String model) {

    public static PracticeFollowUpView none(String reason) {
        return new PracticeFollowUpView(null, reason, null);
    }
}
